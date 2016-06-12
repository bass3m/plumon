
**[Quickstart](#quickstart)** |
**[Configuration](#configuration)** |
**[Plugins](#plugins)** |
**[Adding plugins](#adding-plugins)**

# plumon
------------


An clojure service mainly used for monitoring that supports plugins. 

plumon supports getting metrics through: polling, some push mechanism (like redis pubsub, or rethinkdb). plumon at the moment can output metrics to [riemann](riemann.io). However, in the future, i'm planning to add additional targets.


plumon makes use of plugins in order to make it easy for a user to get his/her metric to riemann/graphite. 

The way i typically use it, is to have plumon feed metrics to riemann. riemann does some basic filtering and sends any alerts to slack/email etc.., riemann then sends certain metrics to [graphite](http://graphite.wikidot.com/). [grafana](grafana.net) is then used as a dashboard.

I've also include the [docker compose](https://docs.docker.com/compose/) config file that i use. 

More on plugins and how to create your own below.

## Quickstart

```
	# configure ip addresses and port numbers in devcfg/procfg.clj
	# setup up graphite ip address in riemann.config
	# from the shell
	# start docker containers using docker-compose
	$ docker-compose up
	# configure
	# start plumon (sudo needed in this case due to ping requiring it)
	$ sudo BOOT_AS_ROOT=yes boot dev-run
```
## Configuration

The following variables are configurable and also optional:

- redis ip address/port (located in `devcfg/prodcfg.clj`)
- riemann ip address/port (located in `devcfg/prodcfg.clj`)
- rethinkdb ip address/port (located in `devcfg/prodcfg.clj`)
- riemann ip address/port (located in `riemann.config`)
- you can add other configurations like tokens, keys etc..
	

## Plugins


#### Plugin basics
A plugin is a functions that gets invoked by plumon every configurable numbers of second (if polled), or is called as a callback for a push type plugin.

Plugins reside in the plugins directory as a separate clojure namespace. In order to start using a plugin you would need to declare how it's configured in the `plugins.edn` file in the resources directory. 


#### plugins.edn

Defines how a plugin would get invoked by plumon:

```
 {:description "redis plugin"
  :run plumon.plugins.redis/run
  :type :riemann
  :kind :redis
  :event {:service "redis test metric"
          :description "some redis test metric"
          :tags ["redis"]}
  :options {:timeout 1000
            :args {:host "127.0.0.1"
                   :metric-key "some:redis:metric"}}}
```

options used in `plugins.edn` file:

- description: text description of the plugin.
- run: clojure namespace of the plugin function to run (in this case we're running the redis plugin run function).
- type (optional): indicates the type of metric. In this case, we're forwarding the metric to riemann. Defaults to just output to stdout.
- kind (optional): the kind of metric to run. possible kinds are: `:redis-pubsub`, `:redis`, `:rethink`. Defaults to a polling metric.
- event (optional) : used in conjuction with riemann. This defines the event that riemann will use to process.
- options : 
	* timeout : timeout in milliseconds. How often to poll metric, applies polling plugins.
	* args : arguments that are passed to the plugin function.
	

## Adding plugins

Plugins can be added by implemeting a function in a separate namespace in the plugins directory. 

Here's a simple example of a plugin (in this case, the plugin does nothing more than return a random number):


```
(ns plumon.plugins.myplugin)

(defn run []
  (let [num (rand-int 1000)]
    (println "myplugin returned: " num)
    num))
```

In order to use this plugin, you would need to declare it in the `plugins.edn` and specify how often it should get called, what kind of event would be used if it's sent to riemann etc.. as described above.

