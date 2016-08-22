(ns plumon.utils
  (:require [taoensso.timbre :as log :refer (tracef debugf infof warnf errorf)]
            [taoensso.carmine :as redis :refer (wcar)]
            [clojure.string :as s]))

(defn get-redis-last
  [conn key]
  (try
    ;; assumes storing in a list
    (wcar conn (redis/lindex key 0))
    (catch Exception e
      (errorf "exception connecting to redis: %s" (.getMessage e))
      0.0)))

(defn log2
  [x]
  (let [[x r] (if (> x 0xffff) [(unsigned-bit-shift-right x 16) 16] [x 0])
        [x r] (if (> x 0xff)   [(unsigned-bit-shift-right x 8) (bit-or r 8)] [x r])
        [x r] (if (> x 0xf)    [(unsigned-bit-shift-right x 4) (bit-or r 4)] [x r])
        [x r] (if (> x 0x3)    [(unsigned-bit-shift-right x 2) (bit-or r 2)] [x r])]
    (bit-or r (bit-shift-right x 1))))
