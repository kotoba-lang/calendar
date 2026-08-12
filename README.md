# calendar

[![CI](https://github.com/kotoba-lang/calendar/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/calendar/actions/workflows/ci.yml)

Portable CLJC model for kotoba-lang/calendar.

The bounded Kotoba profile lives in `src/calendar/model.kotoba` and
`src/calendar/validate.kotoba`. It represents at most 8 nominal events with
keyword IDs and signed-i64 time coordinates. Updates are persistent, event
duration must be positive, and the closed graph requires no host capability.
The open-schema CLJC model remains authoritative for ISO time strings, titles,
attendees, and resource links; those domains are not silently narrowed.

## The overlap rule runs from the shipped Kotoba core

`overlaps?` is decided by `src/calendar/model.kotoba`, compiled to
`resources/calendar/oracle/model.kir.edn` and executed through
`calendar.kotoba-oracle`. `model.cljc/overlaps?` reads the two maps, refuses an
absent instant (`:i64` has no absence), ranks the four instants into the
guest's domain, and calls it — it does not compute the rule. A missing
artifact throws rather than falling back.

`kotoba-lang/kotoba-kir` is therefore a runtime dependency, pinned to the kir
the pinned compiler emits for. The compiler itself stays test-only.

```bash
clojure -M:test:gen   # regenerate resources/calendar/oracle/model.kir.edn
```

Pages editor: https://kotoba-lang.github.io/calendar/

The Pages UI is local to kotoba-lang and does not redirect to external hosts.

## Test

```bash
clojure -M:test
```
