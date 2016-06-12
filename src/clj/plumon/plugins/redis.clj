(ns plumon.plugins.redis
  (:require [taoensso.carmine :as redis :refer (wcar)]))

(defn get-redis-last
  [conn key]
  ;;(println "getting it: conn:" conn ":key:" key "val" (wcar conn (redis/lindex key 0)))
  (try
    (wcar conn (redis/lindex key 0))
    (catch Exception e
      (println (str "exception connecting to redis: " (.getMessage e)))
      0.0)))

(defn run
  [conn {:keys [host metric-key]}]
  (let [sinr-val (get-redis-last conn metric-key)]
    {:metric (Double. sinr-val)}))
