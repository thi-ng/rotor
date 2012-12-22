# rotor

A simple rotating generic log file appender with (optional) support for [Timbre](https://github.com/ptaoussanis/timbre), a new Clojure logging library.

## Leiningen coordinates

    :::clojure
    [com.postspectacular/rotor "0.1.0"]
    
## Usage

The main rotor namespace provides a function `append`, which should be attached as an appender to Timbre's config map:
    
    :::clojure
    (ns user
      (:require
        [taoensso.timbre :as t]
        [com.postspectacular.rotor :as r]))
    
    (t/set-config!
      [:appenders :rotor]
      {:doc "Writes to to (:filename (:rotor :shared-appender-config)) file
             and creates optional backups."
       :min-level :info
       :enabled? true
       :async? false ; should be always false for rotor
       :max-message-per-msecs nil
       :fn r/append})

The appender itself can then be configured like this: 

    :::clojure
    (t/set-config!
      [:shared-appender-config :rotor]
      {:filename "logs/app.log" :max-size (* 512 1024) :history 5})

Only the `:filename` arg is mandatory and can be a relative or absolute path. `:max-size` and `:history` default to the values shown...

Once the log file's size limit has been reached, log rotation is triggered up to a backlog of `:history` files. These backups are suffixed with a 3-digit index, with larger numbers indicating age, e.g.

    logs/app.log     => current log file
    logs/app.log.001 => most recent log file
    logs/app.log.002 => second most recent log file etc.
    
If the max number of files has been reached, the oldest one will be deleted. In future, there will be a suffix fn to customize the naming of archived logs.

## License

Copyright © 2012 Karsten Schmidt // PostSpectacular Ltd.

Distributed under the Eclipse Public License, the same as Clojure.
