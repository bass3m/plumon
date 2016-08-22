(ns plumon.plugins
  (:require [clojure.core.async :as async]
            [clojure.string :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [reloaded.repl :refer [system]]
            [rethinkdb.query :as rethink]
            [taoensso.timbre :as log :refer (tracef debugf infof warnf errorf)]
            [taoensso.carmine :as redis :refer (wcar)]
            [plumon.riemann :as r]))

(defmulti run-plugin (fn [in-chan {:keys [kind]}] kind))

(defn create-redis-listener
  [redis-host redis-port redis-listeners-atom listener-key]
  (try
    (let [listener (redis/with-new-pubsub-listener {:host redis-host :port redis-port} {})]
      (swap! redis-listeners-atom assoc listener-key listener)
      listener)
    (catch Exception e
      (errorf "exception connecting to redis: %s" (.getMessage e))
      false)))

(defn redis-cb
  [redis-conn run options key-name out msg]
  ;;(println "redis-cb: conn:" redis-conn "key-name" key-name "msg:" msg)
  (async/>!! out (or (if (:args options)
                       (run redis-conn msg (:args options))
                       (run redis-conn msg)) false)))

(defmethod run-plugin :redis-pubsub
  [in-chan {:keys [options run] :as plugin}]
  (debugf "redis pubsub push plugin called :plugin: %s" plugin)
  (let [out (async/chan)
        pubsub-chan (-> options :args :channel)
        redis-host (-> options :args :host)
        redis-port (-> options :args :port)
        key-name (or (-> options :args :key-name) "") ;; should warn that it's invalid cfg
        redis-conn {:pool {} :spec {:host redis-host :port (or redis-port 6379)}}
        cb (partial redis-cb redis-conn run options key-name out)
        listener-key (str redis-host ":" redis-port)
        pubsub-key (str redis-host ":" redis-port ":" pubsub-chan)
        listener (or (@(:redis system) listener-key)
                     (create-redis-listener redis-host redis-port (:redis system) listener-key))]
    ;; add the subscription to redis listener
    (debugf "pubsub plugin:options: %s run %s pskey %s" options run pubsub-key)
    (when listener
      (redis/with-open-listener listener
        (if (true? (-> options :args :pattern))
          (redis/psubscribe pubsub-chan)
          (redis/subscribe pubsub-chan)))
      (swap! (:redis-callbacks system) update pubsub-key conj cb)
      (swap! (:state listener) assoc pubsub-chan
             (fn [msg] (dorun (map (fn [f] (f msg)) (@(:redis-callbacks system) pubsub-key))))))
    out))

;; XXX TODO make this an atom and memoize redis connections
(defmethod run-plugin :redis
  [in-chan {:keys [options run] :as plugin}]
  (log/debug "redis run-plugin called :plugin:" plugin)
  (let [out (async/chan)
        conn {:pool {} :spec {:host (-> options :args :host)
                              :port (or (-> options :args :port) 6379)}}]
    (async/go-loop []
      ;; or grab the output from plugin chan
      (let [[v c] (async/alts! [(async/timeout (or (:timeout options) 1000)) in-chan])]
        (condp = c
          in-chan (println "Got incoming msg")
          ;; otherwise must be timeout chan
          (async/>! out (if (:args options) (run conn (:args options)) (run conn)))))
      (recur))
    out))

(defmethod run-plugin :rethink
  [in-chan {:keys [options run] :as plugin}]
  (println "rethinkdb push plugin called. plugin:" plugin)
  (let [out (async/chan)
        changes-chan (-> (rethink/db (-> options :args :db))
                         (rethink/table (-> options :args :table))
                         (rethink/changes {:include-initial true})
                         (rethink/run (:rethink system) {:async? true}))]
    (async/go-loop []
      ;; or grab the output from plugin chan
      (let [[v c] (async/alts! [in-chan changes-chan])]
        (condp = c
          in-chan (println "Got incoming msg")
          ;; otherwise must be timeout chan
          changes-chan (async/>! out (if (:args options) (run v (:args options)) (run v)))
          (println "Got unexpected message from rethink run plugin")))
      (recur))
    out))

;; default is a poll plugin
(defmethod run-plugin :default
  [in-chan {:keys [options run] :as plugin}]
  (println "Default run-plugin called :plugin:" plugin)
  (let [out (async/chan)]
    (async/go-loop []
      ;; or grab the output from plugin chan
      (let [[v c] (async/alts! [(async/timeout (or (:timeout options) 1000)) in-chan])]
        (condp = c
          in-chan (println "Got incoming msg")
          ;; otherwise must be timeout chan
          (async/>! out (if (:args options) (run (:args options)) (run)))))
      (recur))
    out))

(defn load-plugin
  [{:keys [description run] :as plugin}]
  (println (str "loading module: " description ":run:" run))
  ;; require the namespace
  (-> run str (s/split #"/") first symbol require)
  ;; save the resolved symbol
  (merge plugin {:run (resolve run)}))

(defn load-plugins []
  (let [plugins (-> "plugins.edn" io/resource slurp edn/read-string)]
    (map (fn [plugin] (load-plugin plugin)) plugins)))

(defn run-plugins
  [in-chan cfg]
  (let [out (async/chan)
        plugins (load-plugins)
        plugins-with-chans (mapv (fn [p]
                                   (merge p {:out-chan (run-plugin in-chan p)}))
                                 plugins)
        out-chans (mapv :out-chan plugins-with-chans)]
    (async/go-loop []
      (let [[v ch] (async/alts! out-chans)
            which-plugin (reduce (fn [acc p]
                                   (if (= (:out-chan p) ch) (conj acc p) acc)) []
                                   plugins-with-chans)]
        (when v
          (async/>! out {:from (first which-plugin) :val v}))
        (recur)))
    out))

;; called from go-loop in server
(defmulti handle-plugins (fn [{:keys [from]}] (:type from)))

(defmethod handle-plugins :riemann
  [{:as metric :keys [from val]}]
  ;;(println "riemann event: " from ":val:" val)
  (try
    (r/riemann-send {:description (-> from :event :description)
                     :metric (:metric val)
                     :threshold (:threshold val)
                     :service (or (:service val) (-> from :event :service))
                     :tag (-> from :event :tags)
                     :tags (-> from :event :tags)
                     :state (:state val)
                     :opts val})
    (catch Exception e
      (errorf "exception to riemann: %s metric %s" (.getMessage e) metric)
      false)))

(defmethod handle-plugins :default
  [{:as metric :keys [from val]}]
  ;;(println "Default plugin handler. Got: from:" from ":val:" val)
  nil)
