# Ontogeny — LLM API Cost

An estimate, not measured billing — this environment has no `ANTHROPIC_ADMIN_API_KEY`
set, which is what `hyperphor.ellum.providers.anthropic/cost-report` and `usage-report`
need to query the organization's actual Cost/Usage Admin API. Set that env var and those
functions give exact figures instead of the estimate below.

## Basis

Model: `claude-opus-4-8` (ellum's default, unless the UI's provider dropdown is
overridden) — **$5/MTok input, $25/MTok output**.

Each generation is two LLM calls (`hyperphor.ontogeny.schema-gen`'s two-phase design,
see `design.md` §2): list the kinds, then generate the full schema with the kinds list
and a 6.4KB example schema (`resources/jazz-schema-example.edn`) as context. Token
counts below are grounded in a real run — the jazz-musicians generation (33 kinds,
30,628-byte final schema).

| Call | Input | Output | Cost |
|---|---|---|---|
| Phase 1 (list kinds) | ~300 tok | ~600 tok | ~$0.017 |
| Phase 2 (full schema) | ~2,300 tok | ~8,500 tok | ~$0.22 |
| **Total, moderate domain** | | | **~$0.24** |

A large/complex domain (86 kinds, "genetic disorders" during testing) roughly doubles
both phases' output — more like **$0.55–0.65** per generation. Output tokens dominate
the cost: 5x the per-token price of input, and there's a lot more of it than input.

## Failures cost the same as successes

The model has already generated the full response — complete, truncated, or
malformed — before `hyperphor.ontogeny.schema-gen/extract-clojure` ever throws. A
generation that fails parsing burns the same tokens a clean one would have. This
matters because generation is inherently flaky (bad LLM output, occasional malformed
EDN, a `max-tokens` ceiling that's too low for a given domain's size) — see
`schema-gen.clj`'s failure-dump mechanism (`hyperphor.ontogeny.paths/failure-file`) for
capturing what a failed transaction actually cost, not just that it failed.

## Order of magnitude

A day of active development/testing (a dozen or so generation attempts, mixed
successes and deliberate failures, small-to-large domains) ran roughly **$3–5** —
not more than that.
