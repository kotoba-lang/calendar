(ns calendar.kotoba-oracle-test
  "What keeps the shipped artifact honest, now that it is what runs.

  `overlaps-parity-test` compiles `src/calendar/model.kotoba` fresh and
  compares it to `model.cljc`. That was the whole check while the host had its
  own copy of the rule. It is not the whole check any more, because the host
  no longer computes anything — it reads
  `resources/calendar/oracle/model.kir.edn`, and a fresh compile is not that
  file. Two things have to hold that did not have to before:

    1. the shipped artifact IS the current source, compiled
    2. the host actually reads it, rather than having quietly kept a copy

  The second is the one that is easy to lose and impossible to see: a
  delegation that fell back to a host implementation would pass every parity
  test ever written, because a host copy is exactly what those tests compare
  against."
  (:require [calendar.kotoba-oracle :as oracle]
            [calendar.kotoba-oracle-gen :as gen]
            [calendar.model :as model]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.compiler.core :as compiler]))

(deftest the-shipped-artifact-is-the-current-source-compiled
  (doseq [[id source] (sort-by key oracle/cores)]
    (testing (str id " <- " source)
      (let [shipped (edn/read-string (slurp (io/resource (oracle/resource-path id))))
            fresh (:kir (compiler/compile-source (slurp (io/file "src" source))
                                                 gen/target {}))]
        (is (= fresh shipped)
            (str "shipped KIR for " id " is stale — run `clojure -M:test:gen`"))))))

(deftest every-declared-core-actually-ships
  (doseq [id (keys oracle/cores)]
    (is (some? (io/resource (oracle/resource-path id)))
        (str "no artifact for " id))
    (is (some? (oracle/kir id)))))

(deftest a-missing-artifact-throws-rather-than-deciding-anything
  ;; The seam's one refusal. If it fell back instead, the first thing anyone
  ;; would notice is that a decision quietly stopped being the shipped one.
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"shipped decision core is missing"
                        (oracle/kir :not-a-core))))

(def ^:private inverted-source
  "`overlaps?`, negated — same signature, same record type, opposite answer on
  every input. Compiled here rather than hand-written as KIR so that it is a
  real core and not a shape that happens to satisfy the interpreter."
  (str "(ns calendar.model (:export [overlaps?]))"
       "(def event-type"
       "  [:record :calendar/event [[:id :keyword] [:start :i64] [:end :i64]]])"
       "(defn overlaps?"
       "  [left [:alias event-type] right [:alias event-type]] :bool"
       "  (let [a-start (record-get event-type left :start)"
       "        a-end (record-get event-type left :end)"
       "        b-start (record-get event-type right :start)"
       "        b-end (record-get event-type right :end)]"
       "    (if (< a-start a-end)"
       "      (if (< b-start b-end)"
       "        (if (< a-start b-end)"
       "          (if (< b-start a-end) false true)"
       "          true)"
       "        true)"
       "      true)))"))

(defn- ev [id start end]
  {:calendar/id id :calendar/start start :calendar/end end})

(deftest the-host-reads-the-artifact-rather-than-keeping-a-copy
  ;; Swap in a core that answers the OPPOSITE, and require the host to follow.
  ;; A `model.cljc/overlaps?` that had kept its own conjunction of `compare`s
  ;; would not, and nothing else in this repository would say so.
  (let [inverted (:kir (compiler/compile-source inverted-source gen/target {}))
        overlapping [(ev :a 0 10) (ev :b 5 15)]
        disjoint [(ev :a 0 10) (ev :b 20 30)]]
    (is (true? (apply model/overlaps? overlapping)) "the shipped answer")
    (is (false? (apply model/overlaps? disjoint)) "the shipped answer")
    (try
      (oracle/register-kir! :model inverted)
      (is (false? (apply model/overlaps? overlapping))
          "the host followed the artifact")
      (is (true? (apply model/overlaps? disjoint))
          "and followed it in both directions")
      (testing "everything built on the rule follows too, not just the entry point"
        ;; `conflicts` calls `overlaps?` straight through, so if delegation
        ;; were partial -- entry point delegating, callers still on a private
        ;; copy -- this is where it would show.
        (let [cal (-> (model/calendar "cal")
                      (model/add-event (model/event "standup" {:calendar/start 0
                                                               :calendar/end 10}))
                      (model/add-attendee "standup" "person:jun"))
              candidate (model/event "review" {:calendar/start 5 :calendar/end 15})]
          (is (= [] (model/conflicts cal "person:jun" candidate))
              "under the inverted core, overlapping events do not conflict")))
      (finally (oracle/deregister-kir! :model)))
    (is (true? (apply model/overlaps? overlapping)) "restored")
    (is (false? (apply model/overlaps? disjoint)) "restored")))

(deftest the-host-marshals-instants-it-cannot-hand-over-literally
  ;; `:i64` holds neither `nil` nor an ISO-8601 string, and this model stores
  ;; both. What is left on the host side is exactly those two halves, so they
  ;; are asserted here rather than being an implementation detail nobody named.
  (testing "absence is refused before the core is consulted"
    (is (false? (model/overlaps? {:calendar/id :a} (ev :b 0 100))))
    (is (false? (model/overlaps? (ev :a 0 100) {:calendar/id :b}))))
  (testing "string instants reach the core with their order intact"
    (is (true? (model/overlaps? (ev :a "2026-01-01T00:00:00Z" "2026-01-01T01:00:00Z")
                                (ev :b "2026-01-01T00:30:00Z" "2026-01-01T02:00:00Z"))))
    (is (false? (model/overlaps? (ev :a "2026-01-01T00:00:00Z" "2026-01-01T01:00:00Z")
                                 (ev :b "2026-01-01T01:00:00Z" "2026-01-01T02:00:00Z")))
        "half-open, on strings as on integers")
    (is (false? (model/overlaps? (ev :a "2026-01-01T01:00:00Z" "2026-01-01T01:00:00Z")
                                 (ev :b "2026-01-01T00:00:00Z" "2026-01-01T02:00:00Z")))
        "an empty interval occupies no instant, whatever the instants are")))
