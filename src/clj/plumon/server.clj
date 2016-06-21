(ns plumon.server
  (:require [clojure.core.async :as async :refer [<! <!! go-loop thread timeout]]
            [reloaded.repl :refer [system]]
            [clj-time.core :as t]
            [plumon.plugins :as plugins]
            [taoensso.timbre :as log :refer (tracef debugf infof warnf errorf)]
            [environ.core :refer [env]])
  (:gen-class))

(defn enable-logging
  [config]
  (log/merge-config!
   {:appenders {:spit (log/spit-appender {:fname (or (:logfile config) "plumon.log")})}})
  (log/merge-config! {:appenders {:println (log/println-appender {:rate-limit [[1 250] [10 5000]]})}}))

(defn monitor-loop []
  (let [cfg (:cfg system)
        in-chan (async/chan)
        plugins-chan (plugins/run-plugins in-chan cfg)]
    (async/go-loop []
      (let [[v c] (async/alts! [plugins-chan])]
        (condp = c
          plugins-chan (when v (plugins/handle-plugins v))))
      (recur))))

(defn run []
  (enable-logging (:cfg system))
  (monitor-loop))
