(ns plumon.prodcfg
  (require [clojure.java.io :as io]
           [clojure.edn :as edn]))

(defn cfg []
  {:db {:db-type :redis
        :redis-cfg {:pool {} :spec {:host "127.0.0.1" :port 6379}}}
   :stream-processor {:type :riemann :host "0.0.0.0" :port 5555}})
