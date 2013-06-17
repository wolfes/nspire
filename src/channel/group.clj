(ns channel.group
  (:use lamina.core)
  (:gen-class))

; Group channel bookkeeping methods.
; Allows multiple clients to listen to a unique channel name.

(def group-channels (atom {}))

(defn group-channel-with-name? [channel-name]
  "Returns boolean for existance of group channel with channel-name."
  (contains? @group-channels (keyword channel-name)))

(defn get-group-channel-by-name [channel-name]
  "Returns group channel associated with channel-name if one exists, else nil."
  (-> ((keyword channel-name) @group-channels) :channel))

(defn- create-group-channel [channel-name]
  "Upserts new group channel with specified name."
  (dosync
    (swap! group-channels
           (fn [grp-channels]
             (conj grp-channels
                   [(keyword channel-name)
                    {:channel (channel)}])))))

(defn join-group-channel-by-name [channel-name channel]
  "Adds channel as listener to the group channel with channel-name."
  (when-not (group-channel-with-name? channel-name)
   (create-group-channel channel-name)) 
  (when-let [group-channel (get-group-channel-by-name channel-name)]
    (siphon channel group-channel)
    (siphon group-channel channel)))
