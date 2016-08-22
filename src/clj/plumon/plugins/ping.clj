(ns plumon.plugins.ping
  (:require [taoensso.timbre :as log :refer (tracef debugf infof warnf errorf)]))

(defn timed-ping
  "Time an .isReachable ping to a given host. needs sudo"
  [host timeout]
  (let [addr (java.net.InetAddress/getByName host)
        start (. System (nanoTime))
        result (.isReachable addr timeout)
        total (/ (double (- (. System (nanoTime)) start)) 1000000.0)]
    (tracef "ping to: %s took %s result %s" host total result)
    {:metric total
     :state (if result "ok" "error")}))

;; this perhaps should go to riemann config better XXX
(defn run
  [{:keys [tohost timeout threshold]}]
  (let [ping-result (timed-ping tohost timeout)]
    (merge ping-result {:threshold threshold})))
