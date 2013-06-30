(ns app.core
  (:use lamina.core
        aleph.http
        compojure.core
        ring.middleware.params
        channel.private
        channel.group)
  (:require [compojure.route :as route]
            [clojure.data.json :as json])
  (:gen-class))

(defn process-existing-private-channel [ch channel-name]
  "Overwrite existing private channel for channel-name, grand theft channel style."
  (siphon ch ch) ; Echo incoming messages to sender, for client testing.
  (set-private-channel-by-name channel-name ch))

(defn process-new-private-channel [ch channel-name]
  "Store a new private channel by name."
  (set-private-channel-by-name channel-name ch))

(defmulti process-websocket-request
  "Process websocket request by type of command."
  (fn [request ch channel-name cmd]
    (keyword cmd)))

(defmethod process-websocket-request :join-private [request ch channel-name cmd]
  "Add requestor's channel in private channel namespace."
  (println "process-websocket-request > join-private channel-name: " channel-name)
  (if (private-channel-with-name? channel-name)
    (process-existing-private-channel ch channel-name)
    (process-new-private-channel ch channel-name)))

(defmethod process-websocket-request :join-group [request ch channel-name cmd]
  "Add requestor's channel to listen to named channel group."
  (println "process-websocket-request > join-group channel-name: " channel-name)
  (join-group-channel-by-name channel-name ch))

(defn process-api-request [request channel-name cmd]
  "Process http GET api requests to /chat/:room/:cmd.
  For now, send empty command data, since POST requests are preferred."
  (let [channel (get-private-channel-by-name channel-name)
        tabspire-command (json/write-str
                           {:command cmd
                            :command-data {}
                            :channel-name channel-name})]
    (when (channel? channel)
      (enqueue channel tabspire-command))
    {:status 200 :headers {"content-type" "text/html"}
     :body tabspire-command}))

(defn process-api-request-to-channel [request target-channel]
  "Process an API request for a target channel."
  (let [params (:route-params request)
        {:keys [channel-name cmd]} params
        cmd-data (:form-params request)
        tabspire-command (json/write-str
                           {:command cmd
                            :command-data cmd-data
                            :channel-name channel-name})]
    (println "API Request (cmd: " cmd ", form-params: " (:form-params request) ").")
    (when (channel? target-channel)
      (enqueue target-channel tabspire-command))
    {:status 200 :headers {"content-type" "text/html"}
     :body tabspire-command}))

(defmulti route-tabspire-api-post
  "Route POST request to Tabspire API to the appropriate handler.."
  (fn [request]
    "Returns request param's channel-type if available, else :private."
    (keyword (get (:params request) "channel-type" :private))))

(defmethod route-tabspire-api-post :private [request]
  "Process tabspire api request for a private channel."
  (let [channel-name (-> request :route-params :channel-name)
        target-channel (get-private-channel-by-name channel-name)]
    (println "\nTabspire API Post Cmd >> private channel: " channel-name)
    (process-api-request-to-channel request target-channel)))

(defmethod route-tabspire-api-post :group [request]
  "Process tabspire api request for a group channel."
  (let [channel-name (-> request :route-params :channel-name)
        target-channel (get-group-channel-by-name channel-name)]
    (println "\nTabspire API Post Cmd >> group channel: " channel-name)
    (process-api-request-to-channel request target-channel)))

(defn route-tabspire-cmd [req-chan request]
  "Route tabspire api command from websocket or http endpoints.
  req-chan: Channel for queueing responses to requestor.
  "
  (let [params (:route-params request)
        {:keys [channel-name cmd]} params
        is-websocket-request? (:websocket request)]
    (println "\nTabspire API >> Type:"
             (if is-websocket-request? "Websocket" "HTTP API")
             "-- Channel:" channel-name
             "-- Cmd:" cmd)
    (if is-websocket-request?
      (process-websocket-request request req-chan channel-name cmd)
      (enqueue req-chan (process-api-request request channel-name cmd)))))

(def alphanum-regex #"[\:\_\-a-zA-Z0-9]+")

(defroutes app-routes
  "Routes requests to their handler function. Captures dynamic variables."
  (GET ["/tabspire/api/0/:channel-name/:cmd",
        :channel-name , alphanum-regex
        :cmd alphanum-regex] {}
       (wrap-aleph-handler route-tabspire-cmd))
  (POST ["/tabspire/api/0/:channel-name/:cmd"
         :channel-name alphanum-regex
         :cmd alphanum-regex] {}
        (wrap-params route-tabspire-api-post))
  (GET ["/"] {} "Nyan Cat Tabbyspire!")
  ; Route public resources (css/js) to /static url.
  (route/resources "/static")
  ; Handler for requests with no route handler.
  (route/not-found "Page not found"))

(defn -main [& args]
  "Main thread for the server which starts an async server with
  all the routes we specified and is websocket ready."
  (start-http-server (wrap-ring-handler app-routes)
                     {:host "localhost" :port 3000 :websocket true}))
