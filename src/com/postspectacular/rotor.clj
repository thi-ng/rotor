(ns com.postspectacular.rotor
  (:import
   [java.io File FilenameFilter])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]))

(defn str-println
  "Like `println` but prints all objects to output stream as a single
  atomic string. This is faster and avoids interleaving race conditions."
  {:author "Peter Taoussanis"}
  [& xs]
  (print (str (str/join \space xs) \newline))
  (flush))

(defn file-filter
  "Returns a Java FilenameFilter instance which only matches
  files with the given `basename`."
  [basename]
  (reify FilenameFilter
    (accept [_ _ name]
      (.startsWith name basename))))

(defn matching-files
  "Returns a seq of files with the given `basepath` in the
  same directory."
  [basepath]
  (let [f (-> basepath io/file (.getAbsoluteFile))]
    (-> (.getParentFile f)
        (.listFiles (file-filter (.getName f)))
        seq)))

(defn rotate-logs
  "Performs log file rotation for the given files matching `basepath`
  and up to a maximum of `max-count`. Historical versions are suffixed
  with a 3-digit index, e.g.

      logs/app.log     ; current log file
      logs/app.log.001 ; most recent log file
      logs/app.log.002 ; second most recent log file etc.

  If the max number of files has been reached, the oldest one
  will be deleted. In future, there will be a suffix fn to customize
  the naming of archived logs."
  [basepath max-count]
  (let [abs-path (-> basepath io/file (.getAbsolutePath))
        logs (->> basepath
                  matching-files
                  (take max-count)
                  (map #(.getAbsolutePath %))
                  sort
                  reverse)
        num-logs (count logs)
        overflow? (> num-logs max-count)]
    (when overflow?
      (io/delete-file (first logs)))
    (loop [[log & more] (if overflow? (rest logs) logs) n num-logs]
      (when log
        (.renameTo (io/file log) (io/file (format "%s.%03d" abs-path n)))
        (recur more (dec n))))))

(defn append
  "Actual log appender fn for use with Timbre. Requires the following
  keys to be set in timbre's `:shared-appender-config`:

      (timbre/set-config!
        [:shared-appender-config :rotor]
        {:path \"logs/app.log\"
         :max-size (* 512 1024)
         :backlog 5})

  The above settings will write to a file `logs/app.log` in the project folder,
  with a max file size of 512KB and a backlog of 5 files.

  Use the following config as template to create a new appender for Timbre:

      (timbre/set-config!
        {:doc \"Writes to (:filename (:rotor :shared-appender-config)) file and
                creates optional backups.\"
         :min-level :info
         :enabled? true
         :async? false ; should be always false for rotor
         :max-message-per-msecs nil
         :fn rotor/appender})"
  [{:keys [ap-config prefix message more]}]
  (let [{:keys [path max-size backlog]
         :or   {max-size (* 1024 1024)
                backlog 5}} (:rotor ap-config)]
    (when path
      (try
        (when (> (.length (io/file path)) max-size)
          (rotate-logs path backlog))
        (spit path
              (with-out-str
                (apply str-println prefix "-" message more))
              :append true)
        (catch java.io.IOException _)))))
