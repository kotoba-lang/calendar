(ns calendar.model-test
  (:require [clojure.test :refer [deftest is]]
            [calendar.model :as c]
            [calendar.validate :as v]))

(deftest calendar-model
  (let [cal (-> (c/calendar "cal")
                (c/add-event (c/event "a" {:calendar/start "2026-01-01T00:00:00Z"
                                           :calendar/end "2026-01-01T01:00:00Z"}))
                (c/add-attendee "a" "person:jun"))]
    (is (= ["a"] (map :calendar/id (c/events-in-order cal))))
    (is (= ["person:jun"] (:calendar/attendees (c/event-by-id cal "a"))))
    (is (v/valid? cal))))
