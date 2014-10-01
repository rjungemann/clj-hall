# clj-http

Connect to Hall (http://hall.com) from Clojure.

## Usage

First, copy `bashrc.example` to `bashrc` and modify to your needs. Then, run
`source bashrc` to set environment variables.

Next, you can run `lein run` to run a test script which will use your
environment variables.

Following is an example script you can use to try out clj-hall.

```clojure
(ns sample.core
  (:require [clj-hall.core :as hall]))

(defn -main
  [& args]
  (let [; Options hash contains some callback functions
        options {:callbacks {:on-open #(println "CALLBACK open")
                             :on-close #(println "CALLBACK closed")
                             :on-message #(println "CALLBACK message" %)
                             :on-error #(println "CALLBACK error" %)}}

        ; Room ID populated from the bashrc file
        room-id (hall/get-test-room-id)

        ; Construct a client object and connect to Hall
        client (->> (hall/client options) hall/connect!)]

    ; Fetch a list of group rooms
    (println "group rooms" (hall/rooms-request! client))

    ; Fetch a list of room members for a room
    (println "room members" (hall/room-members-request! room-id client))

    ; Fetch a list of pair rooms
    (println "pair rooms" (hall/chats-request! client))

    ; Send a message to a group room
    (println (hall/send-message client room-id "group" "Hello, world!"))))
```

## License

Copyright © 2014 Roger Jungemann.

Distributed under the Eclipse Public License.

