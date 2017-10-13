(ns plumon.utils
  (:require [taoensso.timbre :as log :refer (tracef debugf infof warnf errorf)]
            [taoensso.carmine :as redis :refer (wcar)]
            [clojure.string :as s]))

(defn get-redis-last
  [conn redis-key]
  (try
    ;; assumes storing in a list
    (wcar conn (redis/lindex redis-key 0))
    (catch Exception e
      (errorf "exception getting from redis: %s conn %s key %s" (.getMessage e) conn redis-key)
      0.0)))

(defn log2
  [x]
  (let [x (int x)  ;; make sure it's an int
        [x r] (if (> x 0xffff) [(unsigned-bit-shift-right x 16) 16] [x 0])
        [x r] (if (> x 0xff)   [(unsigned-bit-shift-right x 8) (bit-or r 8)] [x r])
        [x r] (if (> x 0xf)    [(unsigned-bit-shift-right x 4) (bit-or r 4)] [x r])
        [x r] (if (> x 0x3)    [(unsigned-bit-shift-right x 2) (bit-or r 2)] [x r])]
    (bit-or r (bit-shift-right x 1))))
