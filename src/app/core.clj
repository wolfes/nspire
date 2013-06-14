(ns app.core
  (:use lamina.core
        aleph.http
        compojure.core
        ring.middleware.params)
  (:require [compojure.route :as route]
            [clojure.data.json :as json])
  (:gen-class))

; Map private-channel-name to private channel info.
(def private-channels (atom {}))

(defn private-channel-with-name? [channel-name]
  (contains? @private-channels (keyword channel-name)))

(defn get-private-channel-by-name [channel-name]
  (-> ((keyword channel-name) @private-channels) :channel))

(defn set-private-channel-by-name [channel-name channel]
  "Save private channel under name."
  (dosync (swap! private-channels
                 (fn [priv-channels]
                   (conj priv-channels 
                         [(keyword channel-name)
                          {:channel channel :private true}])))))

(defn process-existing-private-channel [ch channel-name]
  "Ideally: Disregard requestor's channel ch.  Lookup private channel.
  Hack: Until we can recognize when to kill off dc'd channel, let new requests
  for the private channel-name replace existing channel.
  Invariant: Keep only one channel per channel-name available to http api."
  (println "Overwriting Existing Channel.")
  ;(println "THIS CHANNEL IS PRIVATE. DO NOT COLLECT $200.")
  (siphon ch ch) ; How this works: Echoes incoming messages to sender.
  ; Replace last private channel for channel-name with new request's channel.
  (set-private-channel-by-name channel-name ch))

(defn process-new-private-channel [ch channel-name]
  "Store a new private channel by name."
  (println "(process-new-private-channel)")
  ;(let [private-channel (named-channel channel-name (partial channel-init channel-name))]
    ;(siphon private-channel ch)
    ;(siphon ch private-channel)
  ; (siphon ch ch) ; How this works: Echoes incoming messages to sender - for testing...
  ; TODO(wstyke): Add handling of existing websocket's channel messages/commands!!.
  ; ...
  (set-private-channel-by-name channel-name ch))

(defn process-websocket-request [ch channel-name]
  "Relays messages into a named channel.
   Creates named channel if one does not already exist."
  (if (private-channel-with-name? channel-name)
    (process-existing-private-channel ch channel-name)
    (process-new-private-channel ch channel-name)))

(defn process-api-request [request channel-name cmd]
  "Process http api requests to /chat/:room/:cmd."
  (let [channel (get-private-channel-by-name channel-name)]
    ; Forward request to websockets listening to named channel.
    (when (not (nil? channel))
      (enqueue channel (str "Command: " cmd ".  Channel:" channel-name))))
  ;(println "Req. Body is a channel: " (channel? (:body request)))
  ;(println "Read Body" (.readLine (:body request)))
  ;(receive-all (:body request) (fn [m] (println "HTTP Message: " m)))
  {:status 200 :headers {"content-type" "text/html"}
   :body (str "Heard req. for channel: " channel-name)})

(defn route-tabspire-cmd [req-chan request]
  "Route tabspire api command from websocket or http endpoints.
    req-chan: Channel for queueing responses to requestor.
  "
  (let [params (:route-params request)
        channel-name (:channel-name params)
        cmd (:cmd params)
        is-websocket-request? (:websocket request)]
    (println "CHAT > Type:"
             (if is-websocket-request? "Websocket" "HTTP API")
             "-- Channel:" channel-name
             "-- Cmd:" cmd)
    (println "CHAT > Req:" request)
    (if is-websocket-request?
      (process-websocket-request req-chan channel-name)
      (enqueue req-chan (process-api-request request channel-name cmd))))
  (println "req-chan: " req-chan)
  (println "DONE route-tabspire-cmd")
  (println))

(defn route-tabspire-api-post [request]
  (println "Tabspire API Post: " request)
  (let [params (:route-params request)
        channel-name (:channel-name params)
        cmd (:cmd params)
        cmd-data (:form-params request)
        private-channel (get-private-channel-by-name channel-name)]
    (when (not (nil? private-channel))
      (enqueue
        private-channel 
        (json/write-str
          {:command cmd
           :command-data cmd-data
           :channel-name channel-name})))
    {:status 200 :headers {"content-type" "text/html"}
     :body (str "Heard req. for channel: " channel-name)}))

(defroutes app-routes
  "Routes requests to their handler function. Captures dynamic variables."
  (GET ["/tabspire/api/0/:channel-name/:cmd", :channel-name #"[a-zA-Z]+", :cmd #"[a-zA-Z]+"] {}
       (wrap-aleph-handler route-tabspire-cmd))
  (POST ["/tabspire/api/0/:channel-name/:cmd", :channel-name #"[a-zA-Z]+", :cmd #"[a-zA-Z]+"] {}
        (wrap-params route-tabspire-api-post))
  (GET ["/"] {} "Nyan Cat Tabbyspire!")
  ;; Route our public resources like css and js to the static url
  (route/resources "/static")
  ;; Any url without a route handler will be served this response
  (route/not-found "Page not found"))
  
(defn -main [& args]
  "Main thread for the server which starts an async server with
  all the routes we specified and is websocket ready."
  (start-http-server (wrap-ring-handler app-routes)
                     {:host "localhost" :port 3000 :websocket true}))

; DEPRECATED FUNCTION GRAVEYARD:
(defn channel-init [channel-name chan]
  "Initialize a new chat channel."
  (receive-all chan #(println "Message from " channel-name ": " %)))

(defn get-channel-by-name [channel-name]
  "Get existing named channel by name, else returns nil."
  (named-channel channel-name (fn [& args] nil)))

(defn named-channel-exists? [channel-name]
  "Returns true if a named channel exists for given channel-name."
  (channel? (get-channel-by-name channel-name)))
