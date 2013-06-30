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
  "Returns group channel for channel-name if one exists, else nil."
  (-> ((keyword channel-name) @group-channels) :channel))

(defn- ensure-group-channel-exists [channel-name]
  "Creates new group channel if none exist for channel-name."
  (dosync
    (when-not (group-channel-with-name? channel-name)
      (swap! group-channels
             (fn [grp-channels]
               (conj grp-channels
                     [(keyword channel-name)
                      {:channel (channel)}]))))))

(defn join-group-channel-by-name [channel-name channel]
  "Adds channel as listener to the group channel with channel-name."
  (ensure-group-channel-exists channel-name)
  (when-let [group-channel (get-group-channel-by-name channel-name)]
    (siphon channel group-channel)
    (siphon group-channel channel)))
