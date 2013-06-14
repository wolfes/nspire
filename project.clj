(defproject app "0.0.0.0"
  :description "Server for connecting Tabspire & Vimspire."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [aleph "0.3.0-alpha2"]
                 [compojure "1.1.1"]
                 [ring "1.1.0-beta2"]
                 [org.clojure/data.json "0.2.2"]]
  :source-path "src/app"
  :aot [app.core]
  :ring {:handler app.core/-main}
  :main app.core)
