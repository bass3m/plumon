(ns plumon.riemann
  (:require [riemann.client :as r]
            [reloaded.repl :refer [system]]))

(defn riemann-send
  [ev]
  (let [riemann (:riemann system)]
    ;;(println "Sending to riemann event:" ev)
    (try
      (-> riemann
          (r/send-event ev)
          (deref 5000 ::timeout))
      (catch Exception e
        (println (str "exception sending to riemann: " (.getMessage e)))
        (str "Failed to send to riemann error: " (.getMessage e))))))
