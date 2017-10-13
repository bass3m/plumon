(ns plumon.devcfg
  (require [clojure.java.io :as io]
           [clojure.edn :as edn]))

(defn cfg []
  {:db {:db-type :redis
        :redis-cfg {:pool {} :spec {:host "0.0.0.0" :port 6379}}}
   :rethink {:host "0.0.0.0" :port 28015 :token 0 :auth-key "" :db "test"}
   :stream-processor {:type :riemann :host "0.0.0.0" :port 5555}})
