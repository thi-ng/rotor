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
  files with the given `base-name`."
  [base-name]
  (reify FilenameFilter
    (accept [_ _ name]
      (.startsWith name base-name))))

(defn matching-files
  "Returns a seq of files with the given base `name` in the
  same directory."
  [name]
  (let [f (-> name io/file (.getAbsoluteFile))]
    (-> (.getParentFile f)
        (.listFiles (file-filter (.getName f)))
        seq)))

(defn rotate-logs
  "Performs log file rotation for the given files matching `base-path`
  and up to a maximum of `max-count`. Historical versions are suffixed
  with a 3-digit index, e.g.

      logs/app.log     ; current log file
      logs/app.log.001 ; most recent log file
      logs/app.log.002 ; second most recent log file etc.

  If the max number of files has been reached, the oldest one
  will be deleted. In future, there will be a suffix fn to customize
  the naming of archived logs."
  [base-path max-count]
  (let [abs-path (-> base-path io/file (.getAbsolutePath))
        [oldest :as logs] (->> base-path
                             matching-files
                             (take max-count)
                             (map #(.getAbsolutePath %))
                             sort
                             reverse)
        num-logs (count logs)]
    (when (>= num-logs max-count)
      (io/delete-file oldest))
    (loop [[l & more] logs n num-logs]
      (when l
        (.renameTo (io/file l)
                   (io/file (format "%s.%03d" abs-path n)))
        (recur more (dec n))))))

(defn append
  "Actual log appender fn for use with Timbre. Requires the following
  keys to be set in timbre's `:shared-appender-config`:

      (timbre/set-config!
        [:shared-appender-config :rotor]
        {:filename \"logs/app.log\"
         :max-size (* 512 1024)
         :history 5})

  The above settings will write to a file `logs/app.log` in the project folder,
  with a max file size of 512KB and a backlog of 5 files.

  Use the following config as template to use this fn as appender for Timbre:

      (timbre/set-config!
        {:doc \"Writes to (:filename (:rotor :shared-appender-config)) file and
                creates optional backups.\"
         :min-level nil
         :enabled? true
         :async? false ; should be always false for rotor
         :max-message-per-msecs nil
         :fn rotor/appender})"
  [{:keys [ap-config prefix message more]}]
  (let [{:keys [filename max-size history]
         :or   {max-size (* 1024 1024)
                history 5}} (:rotor ap-config)]
    (when filename
      (try
        (when (> (.length (io/file filename)) max-size)
          (rotate-logs filename history))
        (spit filename
              (with-out-str
                (apply str-println prefix "-" message more))
              :append true)
        (catch java.io.IOException _)))))
