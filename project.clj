(defproject com.postspectacular/rotor "0.1.0"
  :description "A simple rotating log file appender, e.g. for Timbre."
  :url "http://hg.postspectacular.com/rotor/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :profiles {:test {:dependencies [[com.taoensso/timbre "1.1.0"]]}})
