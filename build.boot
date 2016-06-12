(set-env!
 :source-paths   #{"src/clj"}
 :resource-paths #{"resources"}
 :project 'plumon
 :version "0.1.0-SNAPSHOT"
 :dependencies '[[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]
                 [environ "1.0.1"]
                 [danielsz/boot-environ "0.0.5"]

                 ;; server
                 [org.danielsz/system "0.2.0"]
                 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
                 [clj-http            "2.0.0"]
                 [com.taoensso/timbre "4.1.4"]
                 [com.taoensso/carmine "2.12.2"]
                 [riemann-clojure-client "0.4.2"]
                 [com.apa512/rethinkdb "0.15.23"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]])

(require
 '[reloaded.repl :refer [init start stop go reset]]
 '[plumon.systems :refer [dev-system prod-system]]
 '[danielsz.boot-environ :refer [environ]]
 '[system.boot :refer [system run]])

(task-options!
 pom {;; needed to write the pom.xml file.
      :project (get-env :project)
      :version (get-env :version)
      }
 ;; beware the initial quote here too.
 ;; you could use :all true instead
 aot {:namespace '#{plumon.core}}
 jar {:main 'plumon.core}

 ;; we have our own dev/user.clj file that we wish to load.  We
 ;; skip-init so that we don't clash with Boot's user ns.
 repl {:init-ns 'user
       :eval '(set! *print-length* 20)
       :skip-init true})

;; (replace-task!
;;  [r repl] (fn [& xs]
;;             ;; we only want to have "dev" included for the REPL task
;;             (merge-env! :source-paths #{"dev"})
;;             (apply r xs)))

(deftask build
  "Build the JAR file"
  [] ;; we have no options for this task.

  ;; compose the tasks.
  (comp
   (aot)
   (pom)
   (jar)
   (sift :include #{#"\.jar$"})
   (target)))


(deftask build-uber
  "Build the uberjar file"
  []
  (comp
   (aot)
   (pom)
   (uber)
   ;; In this case, we want the jar to be named in a way that mirrors
   ;; the Leiningen way.
   (jar
    :file (format "%s-%s-standalone.jar"
                  (get-env :project)
                  (get-env :version)))))

(deftask dev
  "Run a restartable system in the Repl"
  []
  (comp
   (environ :env {:http-port 3000})
   (watch :verbose true)
   ;;(system :sys #'dev-system :auto-start true :hot-reload true :files ["handler.clj"])
   (system :sys #'dev-system :auto-start true :hot-reload true)
;;   (reload :on-jsload 'plumon.core/main)
   (repl :server true)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ :env {:http-port 3000})
   (run :main-namespace "plumon.core" :arguments [#'dev-system])
   (wait)))

(deftask prod-run
  "Run a prod system from the command line"
  []
  (comp
   (environ :env {:http-port 8008
                  :repl-port 8009})
   (run :main-namespace "plumon.core" :arguments [#'prod-system])
   (wait)))
