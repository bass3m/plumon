(ns plumon.core
  (:require [reloaded.repl :refer [set-init! go]]
            [plumon.systems :refer [prod-system]]
            [plumon.server :refer [run]])
  (:gen-class))

(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'prod-system)]
    (set-init! system)
    (go)
    (run)))
