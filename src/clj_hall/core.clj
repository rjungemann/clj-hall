(ns clj-hall.core
  (:require [clj-http.client]
            [clojure.data.json]
            [digest])
  (:import (java.net URI)
           (org.java_websocket.client WebSocketClient)
           (org.java_websocket.drafts Draft_17)
           (java.util TimerTask Timer)))

;; =============
;; Client object
;; =============

(defn client
  "Generate an empty Hall client data object."
  [options]
  {:options options
   :cookie-store (clj-http.cookies/cookie-store)
   :start-response-body-data nil
   :signin-response-cookies nil
   :signin-response-body-data nil
   :init-response-body nil
   :websocket nil
   :heartbeat-task nil})

;; ==============
;; Client options
;; ==============

(defn get-options
  "Get the options hash provided when creating the client."
  [client]
  (or (:options client) {}))

(defn get-callbacks
  "Get the callbacks provided in the options hash when creating the client."
  [client]
  (or (:callbacks (get-options client)) {}))

(defn get-callback
  "Get a particular callback by key."
  [client callback-key]
  (let [callbacks (get-callbacks client)
        callback (callback-key callbacks)
        default-callback (fn [& rest])]
    (if (nil? callback)
      default-callback
      callback)))

;; =================
;; Client attributes
;; =================

;; NOTE: For testing
(defn getenv
  "Get an environment variable by key."
  [key]
  (System/getenv key))

(defn get-is-debugging
  "See if the client is running in debug mode. Checks the options hash, the
   HALL_DEBUG env variable, or barring that, returns `false`."
  ([]
   (or (getenv "HALL_DEBUG") false))
  ([client]
   (or (:debug (get-options client)) (get-is-debugging))))

(defn log
  [client & rest]
  (when (get-is-debugging client)
    (apply println rest)))

(defn get-email
  "Fetch the user's hall email from the client options hash, the HALL_EMAIL
   env variable, or barring that, returns a dummy email."
  ([]
   (or (getenv "HALL_EMAIL") "foo@hall-inc.com"))
  ([client]
   (or (:email (get-options client)) (get-email))))

(defn get-password
  "Fetch the user's hall password from the options hash, HALL_PASSWORD env
   variable, or barring that, returns a dummy password."
  ([]
   (or (getenv "HALL_PASSWORD") "barbaz"))
  ([client]
   (or (:password (get-options client)) (get-password))))

(defn get-authenticity-token
  "Get the auth token, which gets stored on the client object once it has made
   a \"start\" request."
  [client]
  (get (:start-response-body-data client) "csrf_token"))

(defn get-socket-id
  "Get the socket id, which gets stored on the client object once it has made an
   \"init\" request."
  [client]
  (let [body (:init-response-body client)]
    (if (nil? body)
      nil
      (first (clojure.string/split body #":")))))

(defn get-user-session-id
  "Once the user has signed in, get the session id out of the client object."
  [client]
  (:value ((:signin-response-cookies client) "user_session_id")))

(defn get-user-token
  "Once the user has signed in, get the token out of the client object."
  [client]
  (get (:signin-response-body-data client) "token"))

(defn get-user-uuid
  "Once the user has signed in, get the uuid out of the client object."
  [client]
  (get (:signin-response-body-data client) "uuid"))

(defn get-user-id
  "Once the user has signed in, get the id out of the client object."
  [client]
  (get (:signin-response-body-data client) "_id"))

(defn get-user-display-name
  "Once the user has signed in, get the id out of the client object."
  [client]
  (or (get (:signin-response-body-data client) "display_name") "Hubot"))

(defn get-user-photo-url
  "Once the user has signed in, get the photo URL out of the client object."
  [client]
  (get (:signin-response-body-data client) "photo_url"))

(defn get-member-data
  "Given the data provided from signing in, create a \"member-data\" hash which
   will be used when making further requests to the server."
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

;; ====
;; Urls
;; ====

(defn get-base-url
  "Get the Hall base URL from the options hash, the NODE_HALL_URL env variable,
   or a default."
  ([]
   (or (getenv "NODE_HALL_URL") "https://hall.com"))
  ([client]
   (or (:base-url (get-options client)) (get-base-url))))

(defn get-api-base-url
  "Get the Hall API base URL from the options hash, the NODE_HALL_URL env
   variable, or a default."
  ([]
   (or (getenv "NODE_HALL_API_URL") "https://hall.com/api/1"))
  ([client]
   (or (:api-url (get-options client)) (get-api-base-url))))

(defn get-stream-base-url
  "Get the Hall stream base URL from the options hash, the NODE_HALL_URL env
   variable, or a default."
  ([]
   (or (getenv "NODE_HALL_STREAMING_URL") "https://stream.hall.com"))
  ([client]
   (or (:streaming-url (get-options client)) (get-stream-base-url))))

(defn get-stream-ws-base-url
  "Get the Hall websocket base URL from the options hash, the NODE_HALL_URL env
   variable, or a default."
  ([]
   (or (getenv "NODE_HALL_STREAMING_WS_URL") "wss://stream.hall.com"))
  ([client]
   (or (:streaming-ws-url (get-options client)) (get-stream-ws-base-url))))

(defn get-stream-base-url-with-params
  "Get the Hall stream URL with all necessary params."
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
  "Get the Hall websocket URL with all necessary params."
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

(defn get-test-room-id
  "Get a test room id from an environment variable or use a dummy value."
  []
  (or (System/getenv "HALL_TEST_ROOM_ID") "12345"))

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
    (assoc client :start-response-body-data response-body-data)))

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
        response-cookies (:cookies response)
        response-body (:body response)
        response-body-data (clojure.data.json/read-str response-body)]
    (assoc client :signin-response-cookies response-cookies
                  :signin-response-body-data response-body-data)))

(defn init-request!
  "Make a request after signing in to prepare the socket."
  [client]
  (let [response-url (get-stream-base-url-with-params client)
        response-options {:headers {"Accept" "application/json"}
                          :cookie-store (:cookie-store client)}
        response (clj-http.client/get response-url response-options)
        response-body (:body response)]
    (assoc client :init-response-body response-body)))

;; ======================
;; Public request methods
;; ======================

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

;; ===============
;; Message sending
;; ===============

(defn send-message!
  "Send a message to a room on Hall using a POST request."
  ([client room-id room-type message]
   (send-message! client room-id room-type message nil))
  ([client room-id room-type message correspondent]
   (let [body-data (if (nil? correspondent)
                     {:type "Comment"
                      :message {:plain message}}
                     {:type "Comment"
                      :message {:plain message}
                      :correspondent_id correspondent})
         response-headers {"User-Agent" "Hall-Node-Client/1.0.2"
                           "Content-Type" "application/json"
                           "Accept" "application/json"
                           "X-CSRF-Token" (get-authenticity-token client)}
         response-url (str (get-api-base-url) "/rooms/" room-type "s/"
                           room-id "/room_items?"
                           "user_api_token=" (get-user-token client))
         response-options {:body (clojure.data.json/write-str body-data)
                           :headers response-headers
                           :cookie-store (:cookie-store client)}]
     (clj-http.client/post response-url response-options))))

;; ==============
;; Socket methods
;; ==============

(defn socket-message
  "Construct a socket message."
  ([level name data]
   (if (and (nil? name) (nil? data))
     (str level "::/room")
     (let [data {:name name :args [data]}
           json (clojure.data.json/write-str data)]
       (str level "::/room:" json))))
  ([level]
   (socket-message level nil nil)))

(defn send-to-socket!
  "Send a socket message over the socket."
  [client data]
  (log client "Sending message" data)
  (.send (:websocket client) data))

(defn send-heartbeat!
  "Send a heartbeat message over the socket to ensure that the server doesn't
   think we've disconnected."
  [websocket]
  (let [ping-message (socket-message 2)]
    (log client "Sending heartbeat" ping-message)
    (.send websocket ping-message)))

(defn heartbeat-task
  "Construct a heartbeat task, which we will trigger every 30 seconds."
  [websocket-atom]
  (proxy [TimerTask] []
    (run []
      (send-heartbeat! (:websocket @websocket-atom)))))

(defn start-heartbeat-task!
  "Start the heartbeat task."
  [task]
  (. (new Timer) (schedule task (long 0) (long 30000))))

(defn join-room-over-socket!
  "Join a room over socket so that we will start receiving socket messages from
   Hall."
  [client]
  (let [data (get-member-data client)
        message (socket-message 5 "join room" data)]
    (log client "Joining room" message)
    (send-to-socket! client message)))

(defn parse-socket-message
  "When we receive a socket message from hall, parse it into an event name and
   event data as a hash."
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
                      (log client "Socket opened" handshake-data)
                      (swap! websocket-atom assoc :websocket this)
                      (send-to-socket! this (socket-message 1))
                      (start-heartbeat-task! task)
                      (join-room-over-socket! this client)
                      ((get-callback client :on-open)))
                    (onClose [code reason is-remote]
                      (log client "Socket closed" code reason is-remote)
                      ((get-callback client :on-close)))
                    (onMessage [message]
                      (let [data (parse-socket-message message)]
                        (if (nil? data)
                          nil
                          ((get-callback client :on-message) data))))
                    (onError [exception]
                      (log client "Socket error" exception)
                      ((get-callback client :on-error exception))))]
    (assoc client :websocket websocket
                  :heartbeat-task task)))

(defn connect-to-socket!
  "Connect to the socket asynchronously."
  [client]
  (.connect (:websocket client))
  client)

(defn connect-to-socket-blocking!
  "Connect to the socket synchronously."
  [client]
  (.connectBlocking (:websocket client))
  client)

;; =====================
;; Public socket methods
;; =====================

(defn connect!
  "Given a constructed client, make all the requests necessary to connect to
   Hall and then connect asynchronously."
  [client]
  (->> client start-request!
              signin-request!
              init-request!
              setup-socket
              connect-to-socket!))

(defn connect-blocking!
  "Given a constructed client, make all the requests necessary to connect to
   Hall and then connect synchronously."
  [client]
  (->> client start-request!
              signin-request!
              init-request!
              setup-socket
              connect-to-socket-blocking!))

(defn disconnect!
  "Disconnect from Hall asynchronously."
  [client]
  (.close (:websocket client))
  client)

(defn disconnect-blocking!
  "Disconnect from Hall synchronously."
  [client]
  (.closeBlocking (:websocket client))
  client)

(defn is-connected
  "Check to see if we are connected to the socket."
  [client]
  (.isOpen client))

(defn is-connecting
  "Check to see if we are connecting to the socket."
  [client]
  (.isConnecting client))

(defn is-disconnected
  "Check to see if we are disconnecting from the socket."
  [client]
  (.isClosed client))

(defn is-disconnecting
  "Check to see if we have disconnected from the socket."
  [client]
  (.isClosing client))

;; ====
;; Main
;; ====

(defn -main
  [& args]
  (let [; Options hash contains some callback functions
        options {:callbacks {:on-open #(println "CALLBACK open")
                             :on-close #(println "CALLBACK closed")
                             :on-message #(println "CALLBACK message" %)
                             :on-error #(println "CALLBACK error" %)}}

        ; Room ID populated from the bashrc file
        room-id (get-test-room-id)

        ; Construct a client object and connect to Hall
        client (->> (client options) connect!)]

    ; Fetch a list of group rooms
    (println "group rooms" (rooms-request! client))

    ; Fetch a list of room members for a room
    (println "room members" (room-members-request! room-id client))

    ; Fetch a list of pair rooms
    (println "pair rooms" (chats-request! client))

    ; Send a message to a group room
    (println (send-message! client room-id "group" "Hello, world!"))

    ; Disconnect the client (commented out)
    (comment (disconnect! client))))

