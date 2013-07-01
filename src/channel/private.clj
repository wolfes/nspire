(ns channel.private
  (:use lamina.core)
  (:gen-class))

; Private channel bookkeeping methods.
; Enforces one client per unique channel name.

; Map private-channel-name to private channel info.
(def private-channels (atom {}))

(defn private-channel-with-name? [channel-name]
  "Returns boolean for existance of private channel with channel-name."
  (contains? @private-channels (keyword channel-name)))

(defn get-private-channel-by-name [channel-name]
  "Returns private channel for channel-name if one exists, else nil."
  ;(-> ((keyword channel-name) @private-channels) :channel))
  (-> @private-channels ((keyword channel-name)) :channel))

(defn set-private-channel-by-name [channel-name channel]
  "Upserts private channel with channel-name."
  (dosync
    (swap! private-channels
           (fn [priv-channels]
             (conj priv-channels
                   [(keyword channel-name) {:channel channel}])))))
