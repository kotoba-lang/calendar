(ns calendar.kotoba-oracle
  "Runs the shipped decision core.

  `src/calendar/model.kotoba` holds the decision;
  `resources/calendar/oracle/model.kir.edn` is what was compiled from it and
  what ships. This namespace is the seam, and it is deliberately thin: it
  resolves a resource, executes an export, and decides nothing.

  ## Why this exists

  `overlaps?` landed in both languages with `overlaps-parity-test` binding
  them, and that test earned its place — it was written because the two
  disagreed, and the one that ran in production was the wrong one. But two
  implementations bound by a test are still two implementations, and the
  measure of this migration is not how many host lines went away; it is
  whether the AUTHORITY moved. Until now it had not: the `.kotoba` was a
  checked replica and the `.cljc` was what ran. Now the `.kotoba` is what
  runs, and `model.cljc` keeps the halves that are not decisions — reading two
  maps, refusing an absent instant, and putting the instants into the guest's
  domain.

  ## No fallback

  A missing or unreadable artifact throws. It does not quietly run a host
  reimplementation, because there is no longer one to run, and because a
  silent fallback is how a decision stops being the one that shipped."
  (:require [clojure.edn :as edn]
            [kotoba.kir :as ir]
            #?(:clj [clojure.java.io :as io])))

(def cores
  "Oracle id -> the .kotoba it was compiled from, under src/."
  {:model "calendar/model.kotoba"})

(defn resource-path [id]
  (str "calendar/oracle/" (name id) ".kir.edn"))

(def ^:private registered
  "Pre-parsed KIR, for runtimes with no classpath (a bundler injects here)."
  (atom {}))

(defn register-kir!
  "Install a parsed KIR for `id`, bypassing the resource read."
  [id kir]
  (swap! registered assoc id kir)
  kir)

(defn deregister-kir!
  "Drop a registration, so `id` reads the shipped artifact again."
  [id]
  (swap! registered dissoc id)
  nil)

(defn- read-artifact [id]
  #?(:clj
     (let [path (resource-path id)]
       (if-let [url (io/resource path)]
         (edn/read-string (slurp url))
         (throw (ex-info "shipped decision core is missing — run `clojure -M:test:gen`"
                         {:oracle id :path path}))))
     :cljs
     (throw (ex-info "no classpath on this runtime — register-kir! first"
                     {:oracle id}))))

(def ^:private cache (atom {}))

(defn kir
  "The shipped KIR for `id`, read once."
  [id]
  ;; A registration wins over the cache: it is an explicit instruction, and a
  ;; caller that registers after something already read the artifact means the
  ;; registration, not the read.
  (or (get @registered id)
      (get @cache id)
      (let [loaded (read-artifact id)]
        (swap! cache assoc id loaded)
        loaded)))

(defn call
  "Execute an export of the shipped core. Args and result are guest ABI
  values; see `record` for the one used here that is not a plain scalar."
  [id export args]
  (ir/execute (kir id) (if (symbol? export) export (symbol (name export))) (vec args)))

(defn record
  "Build a guest record argument: the descriptor, then fields in DECLARED
  order. Declared order, not map order — a record whose fields are permuted
  matches nothing and is refused, which is the behaviour you want, but only if
  you know that is what a permutation costs."
  [schema field-values]
  (into [schema] field-values))
