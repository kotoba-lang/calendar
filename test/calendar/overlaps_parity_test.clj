(ns calendar.overlaps-parity-test
  "Binds `model.kotoba/overlaps?` to `model.cljc/overlaps?`.

  ## Why this exists

  They disagreed. The `.kotoba` requires each event to occupy time —
  `(< start end)` for both, before any comparison — and the `.cljc` did not.
  So for a zero-length or inverted event the two answered differently, and the
  one that runs in production was the permissive one:

      a = {:start 10 :end 10}, b = {:start 0 :end 100}
        .kotoba -> false   (an empty interval intersects nothing)
        .cljc   -> true    (10 < 100 and 0 < 10)

  `validate/event-problems` already called those events
  `:event/non-positive-duration` errors, and `conflicts` calls `overlaps?`
  straight through — so an event that never passed validation produced wrong
  conflict answers, and the rule that said otherwise had never been run.

  Nothing in this repository compared the two: `.github/workflows/ci.yml`
  compiles the conformance module and asserts its `main` returns 42, which
  proves the module links and executes, and says nothing about agreement.

  ## Scope, stated rather than implied

  Instants are `:i64` on the Kotoba side and anything `compare`-able on the
  Clojure side (this repo's events carry ISO-8601 strings). The cases here use
  integers — the overlap, not the whole `.cljc` surface. That the string
  ordering agrees with the integer ordering is ISO-8601's property and is not
  what this test checks."
  (:require [calendar.model :as model]
            [clojure.test :refer [deftest is testing]]
            [kotoba.compiler.core :as compiler]
            ;; At this repo's compiler pin the interpreter still lives inside
            ;; the compiler as `kotoba.compiler.ir`; it moved out to
            ;; `kotoba-lang/kotoba-kir` later (ADR-2607266000 Phase B). Named
            ;; here so a pin advance that breaks this require reads as "the
            ;; interpreter moved", not as "the test is wrong".
            [kotoba.compiler.ir :as ir]))

(def ^:private compiled
  (delay (:kir (compiler/compile-source (slurp "src/calendar/model.kotoba")
                                        :js-kotoba-v1))))

(def ^:private event-type
  "Spelled as `model.kotoba` declares it, so a change of shape fails loudly
  here rather than being followed silently."
  [:record :calendar/event [[:id :keyword] [:start :i64] [:end :i64]]])

(defn- guest-event [id start end] [event-type id start end])

(defn- host-event [id start end]
  {:calendar/id id :calendar/start start :calendar/end end})

(defn- guest-overlaps? [a b]
  (ir/execute @compiled 'overlaps? [a b]))

(def ^:private instants [0 1 5 10 11 100])

(defn- spans []
  (for [s instants e instants] [s e]))

(deftest overlaps-agrees-on-every-pair-of-spans
  (doseq [[as ae] (spans)
          [bs be] (spans)]
    (is (= (model/overlaps? (host-event :a as ae) (host-event :b bs be))
           (guest-overlaps? (guest-event :a as ae) (guest-event :b bs be)))
        (str "[" as "," ae ") vs [" bs "," be ")"))))

(deftest an-empty-event-occupies-no-instant
  ;; The case the two used to disagree on, named so a future reader sees the
  ;; rule rather than a row in a matrix.
  (testing "zero length"
    (is (false? (model/overlaps? (host-event :a 10 10) (host-event :b 0 100))))
    (is (false? (model/overlaps? (host-event :a 0 100) (host-event :b 10 10)))))
  (testing "inverted"
    (is (false? (model/overlaps? (host-event :a 10 5) (host-event :b 0 100))))
    (is (false? (model/overlaps? (host-event :a 0 100) (host-event :b 10 5))))))

(deftest touching-spans-do-not-overlap
  ;; Half-open: a meeting that ends at 10 and one that starts at 10 are
  ;; adjacent, not concurrent. Both implementations already agreed here; it is
  ;; asserted because it is the boundary a future edit is most likely to move.
  (is (false? (model/overlaps? (host-event :a 0 10) (host-event :b 10 100))))
  (is (false? (guest-overlaps? (guest-event :a 0 10) (guest-event :b 10 100)))))

(deftest overlap-is-symmetric-and-irreflexive-for-empty-events
  (doseq [[as ae] (spans)
          [bs be] (spans)
          :let [a (host-event :a as ae) b (host-event :b bs be)]]
    (is (= (model/overlaps? a b) (model/overlaps? b a))
        (str "symmetry " [as ae] " " [bs be])))
  (doseq [[s e] (spans)
          :let [a (host-event :a s e)]]
    (is (= (< s e) (model/overlaps? a a))
        (str "an event overlaps itself exactly when it occupies time " [s e]))))

(deftest a-missing-instant-is-not-an-overlap
  ;; Only the `.cljc` can express this — `:i64` has no absence — so it is
  ;; checked on that side alone rather than pretended to be parity.
  (is (false? (model/overlaps? {:calendar/id :a} (host-event :b 0 100))))
  (is (false? (model/overlaps? (host-event :a 0 100) {:calendar/id :b})))
  (is (false? (model/overlaps? (host-event :a 0 nil) (host-event :b 0 100)))))
