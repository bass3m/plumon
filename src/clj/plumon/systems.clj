(ns plumon.systems
  (:require
   [plumon.devcfg :as dev-cfg]
   [plumon.prodcfg :as prod-cfg]
   [environ.core :refer [env]]
   [system.core :refer [defsystem]]
   [taoensso.carmine :as redis]
   [rethinkdb.query :as r]
   [riemann.client :refer [tcp-client]]
   (system.components
    [repl-server :refer [new-repl-server]])))

(defn connect-to-rethink
  [cfg]
  (try
    (r/connect :host (-> cfg :rethink :host)
               :port (-> cfg :rethink :port)
               :db (-> cfg :rethink :db))
    (catch Exception e
      (println (str "exception connecting to rethink: " (.getMessage e)))
      false)))

(defn redis-listener
  [cfg]
  (try
    (redis/with-new-pubsub-listener (-> cfg :db :redis-cfg :spec) {})
    (catch Exception e
      (println (str "exception connecting to redis: " (.getMessage e)))
      false)))

(defsystem dev-system
  [:cfg (dev-cfg/cfg)
   :redis-listener (redis-listener (dev-cfg/cfg))
   :rethink (connect-to-rethink (dev-cfg/cfg))
   :riemann (tcp-client {:host (-> (dev-cfg/cfg) :stream-processor :host)
                         :port (-> (dev-cfg/cfg) :stream-processor :port)})])

(defsystem prod-system
  [:repl-server (new-repl-server (Integer. (env :repl-port)))
   :cfg (prod-cfg/cfg)
   :riemann (tcp-client {:host (-> (prod-cfg/cfg) :stream-processor :host)
                         :port (-> (prod-cfg/cfg) :stream-processor :port)})])
