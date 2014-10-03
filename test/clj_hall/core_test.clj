(ns clj-hall.core-test
  (:require [clojure.test :refer :all]
            [conjure.core :refer :all]
            [clj-hall.core :refer :all]))

;; TODO: Test `client`
;; TODO: Test `get-options`
;; TODO: Test `get-callbacks`
;; TODO: Test `get-callback`
;; TODO: Test `get-is-debugging`
;; TODO: Test `log`

(deftest get-email-test
  (testing "has a default value"
    (stubbing [getenv (fn [key] nil)]
      (is (= "foo@hall-inc.com" (get-email)))))
  (testing "reads from env if HALL_EMAIL env variable is defined"
    (stubbing [getenv (fn [key] "foo-2@hall-inc.com")]
      (is (= "foo-2@hall-inc.com" (get-email)))))
  (testing "reads from client options if possible"
    (is (= "foo-2@hall-inc.com"
           (get-email {:options {:email "foo-2@hall-inc.com"}})))
    (is (= "foo@hall-inc.com"
           (get-email {:options {}})))
    (is (= "foo@hall-inc.com"
           (get-email {})))))

(deftest get-password-test
  (testing "has a default value"
    (stubbing [getenv (fn [key] nil)]
      (is (= "barbaz" (get-password)))))
  (testing "reads from env if HALL_PASSWORD env variable is defined"
    (stubbing [getenv (fn [key] "bazquux")]
      (is (= "bazquux" (get-password)))))
  (testing "reads from client options if possible"
    (is (= "bazquux"
           (get-password {:options {:password "bazquux"}})))
    (is (= "barbaz"
           (get-password {:options {}})))
    (is (= "barbaz"
           (get-password {})))))

(deftest get-authenticity-token-test
  (testing "returns an authenticity token"
    (let [client {:start-response-body-data {"csrf_token" "1234"}}]
      (is (= "1234" (get-authenticity-token client))))
    (is (nil? (get-authenticity-token {:start-response-body-data {}})))
    (is (nil? (get-authenticity-token {})))))

(deftest get-socket-id-test
  (testing "returns a socket id"
    (let [client {:init-response-body "1234:something_else"}]
      (is (= "1234" (get-socket-id client))))
    (is (nil? (get-socket-id {:init-response-body nil})))
    (is (nil? (get-socket-id {})))))

;; TODO: Test `get-user-session-id`
;; TODO: Test `get-user-token`
;; TODO: Test `get-user-uuid`
;; TODO: Test `get-user-id`
;; TODO: Test `get-user-display-name`
;; TODO: Test `get-user-photo-url`
;; TODO: Test `get-member-data`

(deftest get-base-url-test
  (testing "has a default value"
    (stubbing [getenv (fn [key] nil)]
      (is (= "https://hall.com" (get-base-url)))))
  (testing "reads from env if NODE_HALL_URL env variable is defined"
    (stubbing [getenv (fn [key] "https://other.hall.com")]
      (is (= "https://other.hall.com" (get-base-url)))))
  (testing "reads from client options if possible"
    (is (= "https://other.hall.com"
           (get-base-url {:options {:base-url "https://other.hall.com"}})))
    (is (= "https://hall.com"
           (get-base-url {:options {}})))
    (is (= "https://hall.com"
           (get-base-url {})))))

(deftest get-api-base-url-test
  (testing "has a default value"
    (stubbing [getenv (fn [key] nil)]
      (is (= "https://hall.com/api/1" (get-api-base-url)))))
  (testing "reads from env if NODE_HALL_URL env variable is defined"
    (stubbing [getenv (fn [key] "https://other.hall.com/api/1")]
      (is (= "https://other.hall.com/api/1" (get-api-base-url)))))
  (testing "reads from client options if possible"
    (let [client {:options {:api-url "https://other.hall.com/api/1"}}]
      (is (= "https://other.hall.com/api/1"
             (get-api-base-url client))))
    (is (= "https://hall.com/api/1"
           (get-api-base-url {:options {}})))
    (is (= "https://hall.com/api/1"
           (get-api-base-url {})))))

(deftest get-stream-base-url-test
  (testing "has a default value"
    (stubbing [getenv (fn [key] nil)]
      (is (= "https://stream.hall.com" (get-stream-base-url)))))
  (testing "reads from env if NODE_HALL_URL env variable is defined"
    (stubbing [getenv (fn [key] "https://stream-2.hall.com")]
      (is (= "https://stream-2.hall.com" (get-stream-base-url)))))
  (testing "reads from client options if possible"
    (let [client {:options {:streaming-url "https://stream-2.hall.com"}}]
      (is (= "https://stream-2.hall.com"
             (get-stream-base-url client))))
    (is (= "https://stream.hall.com"
           (get-stream-base-url {:options {}})))
    (is (= "https://stream.hall.com"
           (get-stream-base-url {})))))

(deftest get-stream-ws-base-url-test
  (testing "has a default value"
    (stubbing [getenv (fn [key] nil)]
      (is (= "wss://stream.hall.com" (get-stream-ws-base-url)))))
  (testing "reads from env if NODE_HALL_URL env variable is defined"
    (stubbing [getenv (fn [key] "wss://stream-2.hall.com")]
      (is (= "wss://stream-2.hall.com" (get-stream-ws-base-url)))))
  (testing "reads from client options if possible"
    (let [client {:options {:streaming-ws-url "wss://stream-2.hall.com"}}]
      (is (= "wss://stream-2.hall.com"
             (get-stream-ws-base-url client))))
    (is (= "wss://stream.hall.com"
           (get-stream-ws-base-url {:options {}})))
    (is (= "wss://stream.hall.com"
           (get-stream-ws-base-url {})))))

;; TODO: Test `get-stream-base-url-with-params`
;; TODO: Test `get-stream-ws-url-with-params`

;; TODO: Test `get-test-room-id`

;; TODO: Test `start-request!`
;; TODO: Test `signin-request!`
;; TODO: Test `init-request!`

;; TODO: Test `rooms-request!`
;; TODO: Test `room-members-request!`
;; TODO: Test `chats-request!`

;; TODO: Test `send-message!`

;; TODO: Test `socket-message`
;; TODO: Test `send-to-socket!`
;; TODO: Test `send-heartbeat!`
;; TODO: Test `heartbeat-task`
;; TODO: Test `start-heartbeat-task!`
;; TODO: Test `join-room-over-socket!`
;; TODO: Test `parse-socket-message`
;; TODO: Test `setup-socket`
;; TODO: Test `connect-to-socket!`
;; TODO: Test `connect-to-socket-blocking!`

;; TODO: Test `connect!`
;; TODO: Test `disconnect!`
;; TODO: Test `connect-blocking!`
;; TODO: Test `disconnect-blocking!`
;; TODO: Test `is-connected`
;; TODO: Test `is-connecting`
;; TODO: Test `is-closed`
;; TODO: Test `is-closing`

