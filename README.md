# Ontogeny

Type a domain description ("jazz musicians", "cancer immunotherapy research"), get back
a generated [Alzabo](https://github.com/hyperphor/alzabo) schema and its rendered HTML
documentation — an ontology developed instantly from a one-line description. See
[`design/design.md`](design/design.md) for the full design; the core two-phase
generation is ported from a working prototype, `hyperphor.alzabo.schema-gen-llm`, in the
`hyperphor/alzabo` repo.

## Status

M1: one form, one generation at a time (serialized server-side, see design.md §3), no
persistence UI beyond the generated files sitting on disk. Proves the idea works as a
standalone app before anything fancier.

## Running

```
export ANTHROPIC_API_KEY=...   # or OPENAI_API_KEY, if you switch provider in the UI
lein run 8090
```
then open `http://localhost:8090/`. Requires `graphviz` on `PATH` (Alzabo's HTML doc
generation shells out to it):
```
brew install graphviz
```

For frontend dev iteration, `npm install` once, then either `lein shadow watch app`
(hot-reload) or `lein shadow release app` (optimized, what the `:uberjar` profile's
`prep-tasks` also run).

Hit the API directly without the UI:
```
curl 'http://localhost:8090/api/ontogeny/generate?domain=jazz+musicians'
```
Returns `{"path": "/schema/jazz-musicians-<slug>/index.html"}` — fetch that path for the
rendered doc.

## Development

`com.hyperphor/alzabo` isn't published to Clojars (install-as-local-library only, per
its own README) — run `lein with-profile library,prod install` in the `hyperphor/alzabo`
repo at least once before building this one, or check `~/.m2/repository/com/hyperphor/
alzabo` already has the version this project pins. `bin/link-checkouts.sh` sets up
`checkouts/` symlinks to `way`/`ellum`/`alzabo` for live-editing all four together.

Build a deployable uberjar (runs shadow release + AOT as prep-tasks):
```
lein uberjar
```

## Credits & License

Released under the [Apache 2.0 License](https://opensource.org/license/apache-2-0),
matching Alzabo's.
