(ns plumon.plugins.rethinktest)

(defn run
  [msg {:keys [db table metric-key]}]
  (when msg
    (println "### RETHINK CB ###. msg: " msg ":db:" db ":table:" table ":key:" metric-key))
  ;; return something
  (rand-int 100))
