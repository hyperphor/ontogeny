# Ontogeny — Design

Status: M1, first cut. Extracted from a working prototype, not a from-scratch design.

## 1. What this is

Type a domain description ("jazz musicians", "cancer immunotherapy research"), get
back a generated [Alzabo](https://github.com/hyperphor/alzabo) schema and its rendered
HTML documentation. The name: an ontology developing instantly from a one-line
description, the way an organism develops from a single cell — also a nod to Alzabo's
own paleontology-themed example schemas (`Fossil`, `Taxon`, `AnatomicalPart`).

The core idea isn't new — it's `hyperphor.alzabo.schema-gen-llm` (alzabo repo), a
prototype that already worked well enough to be worth its own app. This repo is that
extraction: a thin Way app wrapping the same two-phase generation, served over HTTP
instead of driven from a REPL.

## 2. The two-phase generation

Ported as-is from the prototype (`hyperphor.ontogeny.schema-gen`), because the two-pass
shape is the actual design insight, not an implementation detail:

1. **Kinds first.** Ask the LLM to enumerate every entity type the domain needs —
   including supporting types that are usually left as lazy strings (materials,
   classifications, controlled vocabularies) — before any field gets written.
2. **Fields second**, with the kinds list in context, so the model uses reference types
   for anything that's already a kind instead of falling back to `:string` everywhere.

A single-shot version of this (ask for the whole schema at once) was the original
version and produced visibly worse schemas — over-stringly, under-referenced. Don't
collapse the two calls to save a round trip.

## 3. Rendering

Alzabo already generates HTML docs from a schema file
(`hyperphor.alzabo.core/do-command :documentation`). `hyperphor.ontogeny.doc-gen` writes
the generated schema to `resources/generated/<slug>.edn` and renders its doc to
`resources/public/schema/<slug>/`, which Way's static resource middleware serves
directly — the same trick `nlq-aact` uses to iframe its own (database-derived, not
LLM-derived) schema doc. `<slug>` is the domain text slugified plus a base-36 timestamp,
so repeat generations of the same domain don't collide and stay browsable side by side.

**Known limitation**: Alzabo's config (`hyperphor.alzabo.config/the-config`) is a single
global atom, read mid-render including a graphviz subprocess call. `doc-gen` serializes
all generation requests behind one lock rather than fix this upstream — fine for a
low-traffic demo, worth revisiting if that stops being true.

## 4. What's deliberately not here yet

- **Subtypes/extends relations.** The prototype's comment notes the current prompting
  doesn't produce them; carried over as a known gap, not silently dropped.
- **Persistence of past generations** beyond what's sitting in `resources/generated`/
  `resources/public/schema` on disk — no listing UI, no way to revisit an old one except
  by URL.
- **Any editing of the generated schema.** Output is read-only HTML; if hand-tweaking
  turns out to matter, that's a real follow-on, not a trivial one (Alzabo's format isn't
  built for round-tripping through a UI editor).

## 5. Deploy note

Alzabo's doc generation shells out to `graphviz`. Wherever this gets hosted needs
graphviz on `PATH` — a buildpack/build-step concern, not just `brew install` locally.
