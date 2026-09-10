# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## What this is

Ontogeny: type a domain description, get back a generated Alzabo schema and its
rendered HTML documentation. Extracted from `hyperphor.alzabo.schema-gen-llm` (a
prototype in the `hyperphor/alzabo` repo) into its own standalone app. Full design:
`design/design.md`.

`com.hyperphor/way` (web app framework: server, config, frontend shell, ring handler),
`com.hyperphor/ellum` (multi-provider LLM client), and `com.hyperphor/alzabo` (schema
format + HTML doc generation) are external library dependencies (Maven coordinates in
`project.clj`), not part of this repo. `com.hyperphor/nlq` is deliberately *not* a
dependency, same reasoning as `eli` — no NL-to-SQL step here, just LLM generation +
Alzabo rendering.

**Not published on Clojars**: `com.hyperphor/alzabo` is install-as-local-library only.
Run `lein with-profile library,prod install` in `hyperphor/alzabo` before building this
repo if `~/.m2/repository/com/hyperphor/alzabo` doesn't already have the pinned version.

## Commands

```
# Run the server (reads ANTHROPIC_API_KEY/OPENAI_API_KEY from env; PORT as arg or env)
export ANTHROPIC_API_KEY=...
lein run 8090
# -> open http://localhost:8090/

# Frontend dev iteration (after `npm install` once)
lein shadow watch app       # hot-reload cljs
lein shadow release app     # optimized build (what :uberjar's prep-tasks also run)

# Hit the API directly without the UI
curl 'http://localhost:8090/api/ontogeny/generate?domain=jazz+musicians'

# Build a deployable uberjar (runs shadow release + AOT as prep-tasks)
lein uberjar
```

There is no Clojure test suite yet.

## Architecture

Backend (`src/clj/hyperphor/ontogeny/`):

- **`core.clj`** — `-main`: loads `resources/config.edn` via `hyperphor.way.config`,
  starts `hyperphor.way.server` with the handler's routes.
- **`handler.clj`** — `GET /api/ontogeny/generate` is the only API route. Calls
  `schema-gen/sgen` then `doc-gen/generate`; catches and reports generation failures as
  `{:error ...}` rather than a bare 500 (generation is inherently flaky — bad LLM output,
  graphviz failures).
- **`schema-gen.clj`** — the ported two-phase generation (`generate-kinds`, then
  `generate-schema-from-kinds`). See its docstring and design.md §2 before changing the
  shape — collapsing to one LLM call was tried in the original prototype and produced
  worse schemas.
- **`doc-gen.clj`** — writes the generated schema to `resources/generated/<slug>.edn`
  and renders its HTML doc to `resources/public/schema/<slug>/` via
  `hyperphor.alzabo.core/do-command`, which Way's static resource middleware then
  serves directly. Serializes all generation calls behind one lock — see its docstring
  for why (Alzabo's own config is a single global atom).

Frontend (`src/cljs/hyperphor/ontogeny/frontend/`):

- **`core.cljs`** — app shell: a form (domain/extra/provider), then an iframe onto the
  generated doc once ready. One page, no tabs.
- **`events.cljs`** — app-db + the `:ontogeny/generate` event, which calls the API as a
  side effect inside a plain `reg-event-db` handler (same pattern as
  `hyperphor.nlq.frontend.qbox`'s `:qbox-query` — no `:http-xhrio` effect is wired up
  anywhere in this stack).

## A real `ellum.extract` gotcha, worked around locally

`hyperphor.ellum.extract/extract-clojure` and `/extract-edn` have inconsistent contracts:
`extract-clojure` only handles a ` ``` `-fenced response (nil otherwise, no fallback);
`extract-edn` falls back to bare `read-string` but then returns the parsed value
*directly* on that path vs. a `[value remaining-text]` pair when the fence-matching path
succeeds. Claude and GPT are both inconsistent about whether they fence a bare-EDN
answer at all, so this bit in testing (a `ClassCastException` several calls deep, from
treating a `[map ""]` pair as the map itself). `schema-gen.clj`'s `read-edn-response`
handles both shapes locally rather than relying on either helper's contract — worth
raising with the `ellum` agent as a shared-library fix rather than re-solving per-app.

## Known gaps (see design.md §4)

- No subtype/extends relations in generated schemas (a gap in the original prompt,
  carried over deliberately rather than silently dropped).
- No persistence/listing UI for past generations beyond the files sitting on disk.
- Output is read-only HTML; no round-trip editing of the generated schema.
