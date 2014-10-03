# clj-http

Connect to Hall (http://hall.com) from Clojure. This is capable of doing
everything the [node-hall-client](http://github.com/Hall/node-hall-client), and
a few extra things as well.

## What can you use this for?

* Listen for a user's name, or for them to come online.
* Bots (Lisp has a long history in AI research, so a lot of possibility there)
* Custom integration with third-party services
* Runs on JVM, so can leverage all of the existing JVM infrastructure, yet can
  deploy on (for example) Heroku.

## Usage

You can modify some environment variables and use them to run a test script.
First, copy `bashrc.example` to `bashrc` and modify to your needs. Then, run
`source bashrc` to set environment variables. Next, you can run `lein run` to
run a test script which will use your environment variables.

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
    (println (hall/send-message! client room-id "group" "Hello, world!"))
    
    ; Disconnect the client (commented out)
    (comment (hall/disconnect! client))))
```

When setting up the client, it is often preferable to use the options hash to
set the email and password, like so:

```clojure
(->> (hall/client {:email "foo@hall-inc.com" :password "blah"}) hall/connect)
```

Note the use of the `thrush` operator, which makes it simpler to construct your
client object and apply operations on it. It is equivalent to:

```clojure
(hall/connect (hall/client {:email "foo@hall-inc.com" :password "blah"}))
```

## API

* `(hall/client options)` - Create a new `client` object. The options hash can
  contain the following:
  * `:callbacks` - A hash whose keys are symbols and whose values are functions:
    * `on-open` - A function with no parameters.
    * `on-close` - A function with no parameters.
    * `on-message` - A function with one parameter, a hash containing `:name`,
      a new of an event, and `:data`, a hash containing data from the server.
    * `on-error` - A funciton with one parameter, a Java Exception object.
  * `:debug` - A boolean, which, when true, will log additional data during
    operation.
  * `:email` - An email address to login to Hall.
  * `:password` - A password to login to Hall.
  * `:base-url` - The base address to Hall (optional).
  * `:api-url` - The API endpoint for Hall (optional).
  * `:streaming-url` - Address for streaming information to Hall (optional).
  * `:streaming-ws-url` - A websocket URL to Hall (optional).
* `(hall/connect! client)` - Setup the client and connect to Hall
  asynchronously. Will return a copied and modified client object.
* `(hall/rooms-request! client)` Returns a clj-http response object containing
  data about rooms the client has access to.
* `(hall/room-members-request! room-id client)` Returns a clj-http response
  object containing data about members of a room by room id.
* `(hall/chats-request! client)` Returns a clj-http response object containing
  data about pair rooms the client has access to.
* `(hall/send-message! client)` Sends a message to a room of a certain type and
  returns a clj-http response object.
* `(hall/disconnect! client)` Disconnect the client from the socket.
* `(hall/connect-blocking! client)` Connect to the socket synchronously,
* `(hall/disconnect-blocking! client)` Disconnect from the socket synchronously,
* `(hall/is-connected client)`, `(hall/is-connecting client)`,
  `(hall/is-disconnected client)`, `(hall/is-disconnecting client)` - Examine
  the status of the socket.

## TODO

Although we respond to server heartbeats with our own, the client should still
ocassionaly do its own connection checking, like the
[node-hall-client](http://github.com/Hall/node-hall-client) does.

## License

Copyright © 2014 Roger Jungemann.

Distributed under the Eclipse Public License.

