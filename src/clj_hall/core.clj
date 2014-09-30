(ns clj-hall.core
  (:require [clj-http.client]
            [clojure.data.json]
            [digest])
  (:import (java.net URI)
           (org.java_websocket.client WebSocketClient)
           (org.java_websocket.drafts Draft_17)
           (java.util TimerTask Timer)))

;; TODO: Disconnecting
;; TODO: Client-side heartbeat
;; TODO: Sending messages

;; =============
;; Client object
;; =============

(defn client
  "Generate an empty Hall client data object"
  [options]
  {:options options
   :cookie-store (clj-http.cookies/cookie-store)
   :start-response nil
   :start-response-body-data nil
   :signin-response nil
   :signin-response-body-data nil
   :init-response nil
   :init-response-body nil
   :websocket nil
   :heartbeat-task nil})

;; =================
;; Client attributes
;; =================

(defn get-email
  "Fetch the user's hall email from the HALL_EMAIL environment variable."
  []
  (or (System/getenv "HALL_EMAIL") "foo@hall-inc.com"))

(defn get-password
  "Fetch the user's hall password from the HALL_PASSWORD environment variable."
  []
  (or (System/getenv "HALL_PASSWORD") "barbaz"))

(defn get-authenticity-token
  "Once the user has made the start request, fetch the auth token out of the
  client object."
  [client]
  (get (:start-response-body-data client) "csrf_token"))

(defn get-socket-id
  "Once the user has made the init request, fetch the socket id out of the
  client object."
  [client]
  (first (clojure.string/split (:init-response-body client) #":")))

(defn get-user-session-id
  "Once the user has signed in, fetch the session id out of the client object."
  [client]
  (:value ((:cookies (:signin-response client)) "user_session_id")))

(defn get-user-token
  "Once the user has signed in, fetch the token out of the client object."
  [client]
  (get (:signin-response-body-data client) "token"))

(defn get-user-uuid
  "Once the user has signed in, fetch the uuid out of the client object."
  [client]
  (get (:signin-response-body-data client) "uuid"))

(defn get-user-id
  [client]
  (get (:signin-response-body-data client) "_id"))

(defn get-user-display-name
  [client]
  (or (get (:signin-response-body-data client) "display_name") "Hubot"))

(defn get-user-photo-url
  [client]
  (get (:signin-response-body-data client) "photo_url"))

(defn get-member-data
  [client]
  {:uuid "globalHall"
   :member {:name (get-user-display-name client)
            :id (get-user-id client)
            :hall_member_id nil
            :hall_uuid nil
            :photo_url (get-user-photo-url client)
            :mobile false
            :native false
            :admin false}
   :member_uuid (get-user-uuid client)})

;; ==============
;; Client options
;; ==============

(defn get-options
  [client]
  (or (:options client) {}))

(defn get-callbacks
  [client]
  (or (:callbacks (get-options client)) {}))

(defn get-callback
  [client callback-key]
  (let [callbacks (get-callbacks client)
        callback (callback-key callbacks)
        default-callback (fn [& rest])]
    (if (nil? callback)
      default-callback
      callback)))

;; ====
;; Urls
;; ====

(defn get-base-url
  "Fetch the Hall base URL from the NODE_HALL_URL environment variable."
  []
  (or (System/getenv "NODE_HALL_URL") "https://hall.com"))

(defn get-api-base-url
  "Fetch the Hall API base URL from the NODE_HALL_URL environment variable."
  []
  (or (System/getenv "NODE_HALL_API_URL") "https://hall.com/api/1"))

(defn get-stream-base-url
  "Fetch the Hall stream base URL from the NODE_HALL_URL environment variable."
  []
  (or (System/getenv "NODE_HALL_STREAMING_URL") "https://stream.hall.com"))

(defn get-stream-ws-base-url
  "Fetch the Hall websocket base URL from the NODE_HALL_URL environment
  variable."
  []
  (or (System/getenv "NODE_HALL_STREAMING_WS_URL") "wss://stream.hall.com"))

(defn get-stream-base-url-with-params
  "Fetch the Hall stream URL with all necessary params."
  [client]
  (let [user-session-id (get-user-session-id client)
        uuid (get-user-uuid client)
        authenticity-token (get-authenticity-token client)
        stream-base-url (get-stream-base-url)
        session (digest/md5 (str uuid authenticity-token))]
    (str stream-base-url
         "/socket.io/1?"
         "user_session_id=" user-session-id
         "&id=" uuid
         "&session=" session)))

(defn get-stream-ws-url-with-params
  "Fetch the Hall websocket URL with all necessary params."
  [client]
  (let [user-session-id (get-user-session-id client)
        uuid (get-user-uuid client)
        authenticity-token (get-authenticity-token client)
        stream-ws-base-url (get-stream-ws-base-url)
        socket-id (get-socket-id client)
        session (digest/md5 (str uuid authenticity-token))]
    (str stream-ws-base-url
         "/socket.io/1/websocket/" socket-id "?"
         "user_session_id=" user-session-id
         "&id=" uuid
         "&session=" session)))

;; ===============
;; Request methods
;; ===============

(defn start-request!
  "Make the initial request to Hall which will retrieve some initial tokens."
  [client]
  (let [response-url (str (get-base-url) "/s")
        response-headers {"User-Agent" "Hall-Node-Client/1.0.2"
                          "Content-Type" "application/json"
                          "Accept" "application/json"}
        response-options {:headers response-headers
                          :cookie-store (:cookie-store client)}
        response (clj-http.client/get response-url response-options)
        response-body (:body response)
        response-body-data (clojure.data.json/read-str response-body)]
    (assoc client :start-response response
                  :start-response-body-data response-body-data)))

(defn signin-request!
  "Make a request to Hall to signin the user."
  [client]
  (let [user-data {:email (get-email)
                   :password (get-password)}
        response-url (str (get-base-url) "/users/sign_in")
        response-body (clojure.data.json/write-str {:user user-data})
        response-headers {"User-Agent" "Hall-Node-Client/1.0.2"
                          "Content-Type" "application/json"
                          "Accept" "application/json"}
        response-options {:body response-body
                          :headers response-headers
                          :cookie-store (:cookie-store client)}
        response (clj-http.client/post response-url response-options)
        response-body (:body response)
        response-body-data (clojure.data.json/read-str response-body)]
    (assoc client :signin-response response
                  :signin-response-body-data response-body-data)))

(defn init-request!
  "Make a request after signing in to prepare the socket."
  [client]
  (let [response-url (get-stream-base-url-with-params client)
        response-options {:headers {"Accept" "application/json"}
                          :cookie-store (:cookie-store client)}
        response (clj-http.client/get response-url response-options)
        response-body (:body response)]
    (assoc client :init-response response
                  :init-response-body response-body)))

(defn rooms-request!
  "Fetch a list of rooms. Requires signing in."
  [client]
  (let [response-url (str (get-api-base-url) "/rooms/groups?"
                          "user_api_token=" (get-user-token client))
        response-options {:cookie-store (:cookie-store client)}]
    (clj-http.client/get response-url response-options)))

(defn room-members-request!
  "Fetch a list of room members in a room. Requires signing in."
  [client room-id]
  (let [response-url (str (get-api-base-url) "/rooms/groups/"
                          room-id "/room_members?"
                          "user_api_token=" (get-user-token client))
        response-options {:cookie-store (:cookie-store client)}]
    (clj-http.client/get response-url response-options)))

(defn chats-request!
  "Fetch a list of chats. Requires signing in."
  [client]
  (let [response-url (str (get-api-base-url) "/rooms/pairs?"
                          "user_api_token=" (get-user-token client))
        response-options {:cookie-store (:cookie-store client)}]
    (clj-http.client/get response-url response-options)))

;; ==============
;; Socket methods
;; ==============

(defn socket-message
  ([level name data]
   (if (and (nil? name) (nil? data))
     (str level "::/room")
     (let [data {:name name :args [data]}
           json (clojure.data.json/write-str data)]
       (str level "::/room:" json))))
  ([level]
   (socket-message level nil nil)))

(defn send-to-socket!
  [websocket data]
  (println "Sending message" data)
  (.send websocket data))

(defn send-heartbeat!
  [websocket]
  (let [ping-message (socket-message 2)]
    (println "Sending heartbeat" ping-message)
    (.send websocket ping-message)))

(defn heartbeat-task
  [websocket-atom]
  (proxy [TimerTask] []
    (run []
      (send-heartbeat! (:websocket @websocket-atom)))))

(defn start-heartbeat-task!
  [task]
  (. (new Timer) (schedule task (long 0) (long 30000))))

(defn join-room-over-socket!
  [websocket client]
  (let [data (get-member-data client)
        message (socket-message 5 "join room" data)]
    (println "Joining room" message)
    (send-to-socket! websocket message)))

(defn parse-socket-message
  [message]
  (let [matches (re-matches #"(.*)?::(.*?):(.*)" message)
        id (or (nth matches 1) "")
        path (or (nth matches 2) "")
        json (or (nth matches 3) "{}")
        data (or (clojure.data.json/read-str json) {})
        method-name (get data "name")
        method-args (or (get data "args") [])
        method-body (first method-args)]
    (if (nil? method-name)
      nil
      {:name method-name
       :data method-body})))

(defn setup-socket
  "Connect to Hall over the websocket. Requires signing in."
  [client]
  (let [websocket-atom (atom {:websocket nil})
        task (heartbeat-task websocket-atom)
        websocket (proxy [WebSocketClient]
                         [(new URI (get-stream-ws-url-with-params client))
                          (new Draft_17)]
                    (onOpen [handshake-data]
                      (println "Socket opened" handshake-data)
                      (swap! websocket-atom assoc :websocket this)
                      (send-to-socket! this (socket-message 1))
                      (start-heartbeat-task! task)
                      (join-room-over-socket! this client)
                      ((get-callback client :on-open)))
                    (onClose [code reason is-remote]
                      (println "Socket closed" code reason is-remote)
                      ((get-callback client :on-close)))
                    (onMessage [message]
                      (let [data (parse-socket-message message)]
                        (if (nil? data)
                          nil
                          ((get-callback client :on-message) data))))
                    (onError [exception]
                      (println "Socket error" exception)
                      ((get-callback client :on-error exception))))]
    (assoc client :websocket websocket
                  :heartbeat-task task)))

(defn connect-to-socket!
  [client]
  (.connect (:websocket client))
  client)

;; ====
;; Main
;; ====

(defn -main
  [& args]
  (let [options {:callbacks {:on-open #(println "CALLBACK open")
                             :on-close #(println "CALLBACK closed")
                             :on-message #(println "CALLBACK message" %)
                             :on-error #(println "CALLBACK error" %)}}
        client (->> (client options) start-request!
                                     signin-request!
                                     init-request!
                                     setup-socket
                                     connect-to-socket!)
        rooms (rooms-request! client)]
    (println "rooms" rooms))

