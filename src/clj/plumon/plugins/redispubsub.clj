(ns plumon.plugins.redispubsub
  (:require [taoensso.carmine :as redis :refer (wcar)]))

(defn run
  [msg conn {:keys [host port channel]}]
  (println "redis pubsub cb. msg: " msg ":host:" host ":port:" port ":channel:" channel)
  ;; return something
  (rand-int 100))
