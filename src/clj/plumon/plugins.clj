(ns plumon.plugins
  (:require [clojure.core.async :as async]
            [clojure.string :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [reloaded.repl :refer [system]]
            [rethinkdb.query :as rethink]
            [taoensso.carmine :as redis :refer (wcar)]
            [plumon.riemann :as r]))

(defmulti run-plugin (fn [in-chan {:keys [kind]}] kind))

(defmethod run-plugin :redis-pubsub
  [in-chan {:keys [options run] :as plugin}]
  ;; grab from system
  (println "redis pubsub push plugin called :plugin:" plugin)
  (let [out (async/chan)
        pubsub-chan (s/join ":" [(-> options :args :module) (-> options :args :redis-channel)])]
    ;; add the subscription to redis listener
    (redis/with-open-listener (:redis-listener system)
      (redis/subscribe pubsub-chan))
    (swap! (:state (:redis-listener system)) assoc pubsub-chan
           (fn [msg]
             (async/>!! out (if (:args options) (run msg (:args options)) (run msg)))))
    out))

;; XXX TODO make this an atom and memoize redis connections
(defmethod run-plugin :redis
  [in-chan {:keys [options run] :as plugin}]
  (println "redis run-plugin called :plugin:" plugin)
  (let [out (async/chan)
        conn {:pool {} :spec {:host (-> options :args :host) :port 6379}}]
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
        ;;(println "Got output from plugin:" which-plugin ":val:" v)
        (async/>! out {:from (first which-plugin) :val v})
        (recur)))
    out))

;; called from go-loop in server
(defmulti handle-plugins (fn [{:keys [from]}] (:type from)))

(defmethod handle-plugins :riemann
  [{:as metric :keys [from val]}]
  ;;(println "riemann event: " from ":val:" val)
  (r/riemann-send {:description (-> from :event :description)
                   :metric (:metric val)
                   :threshold (:threshold val)
                   :service (-> from :event :service)
                   :tag (-> from :event :tags)
                   :state (:state val)
                   :opts val}))

(defmethod handle-plugins :default
  [{:as metric :keys [from val]}]
  ;;(println "Default plugin handler. Got: from:" from ":val:" val)
  nil)
