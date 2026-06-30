(ns calendar.model)

(defn calendar
  ([id] (calendar id {}))
  ([id attrs]
   (merge {:calendar/id id
           :calendar/type :calendar
           :calendar/events {}}
          attrs)))

(defn event [id attrs]
  (merge {:calendar/id id
          :calendar/title id
          :calendar/start nil
          :calendar/end nil
          :calendar/attendees []
          :calendar/links []}
         attrs))

(defn add-event [cal ev]
  (assoc-in cal [:calendar/events (:calendar/id ev)] ev))

(defn event-by-id [cal id]
  (get-in cal [:calendar/events id]))

(defn add-attendee [cal event-id person-id]
  (update-in cal [:calendar/events event-id :calendar/attendees] (fnil conj []) person-id))

(defn link-resource [cal event-id resource]
  (update-in cal [:calendar/events event-id :calendar/links] (fnil conj []) resource))

(defn events-in-order [cal]
  (->> (vals (:calendar/events cal))
       (sort-by (juxt :calendar/start :calendar/id))
       vec))

(defn overlaps? [a b]
  (and (:calendar/start a) (:calendar/end a)
       (:calendar/start b) (:calendar/end b)
       (neg? (compare (:calendar/start a) (:calendar/end b)))
       (neg? (compare (:calendar/start b) (:calendar/end a)))))

(defn seed-calendar []
  (-> (calendar "gftd-calendar")
      (add-event (event "weekly-briefing" {:calendar/title "Weekly briefing"
                                           :calendar/start "2026-07-01T09:00:00Z"
                                           :calendar/end "2026-07-01T09:30:00Z"
                                           :calendar/links [{:kind :briefing :id "weekly"}]}))))
