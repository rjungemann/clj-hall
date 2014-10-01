(defproject clj-hall "0.1.0"
  :description "A Hall client for Clojure."
  :url "http://github.com/thefifthcircuit/clj-hall"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main clj-hall.core
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/java.jdbc "0.3.2"]
                 [postgresql "9.1-901.jdbc4"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [compojure "1.1.6"]
                 [stencil "0.3.4"]
                 [org.java-websocket/Java-WebSocket "1.3.0"]
                 [clj-http "1.0.0"]
                 [org.clojure/data.json "0.2.5"]
                 [digest "1.4.4"]])

