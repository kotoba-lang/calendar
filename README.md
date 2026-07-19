# calendar

[![CI](https://github.com/kotoba-lang/calendar/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/calendar/actions/workflows/ci.yml)

Portable CLJC model for kotoba-lang/calendar.

The bounded Kotoba profile lives in `src/calendar/model.kotoba` and
`src/calendar/validate.kotoba`. It represents at most 8 nominal events with
keyword IDs and signed-i64 time coordinates. Updates are persistent, event
duration must be positive, and the closed graph requires no host capability.
The open-schema CLJC model remains authoritative for ISO time strings, titles,
attendees, and resource links; those domains are not silently narrowed.

Pages editor: https://kotoba-lang.github.io/calendar/

The Pages UI is local to kotoba-lang and does not redirect to external hosts.

## Test

```bash
clojure -M:test
```
