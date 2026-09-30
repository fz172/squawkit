# Task suggestion eval

Runs the real suggestion pipeline (`src/ai/tasks`) against fixed cases with a chosen provider
pair and scores it against PRD §9.4. Design: [`docs/ai/task_population_design.md` §12](../../../../docs/ai/task_population_design.md#12-evaluation-harness-phase-0).

A live run costs money and needs credentials, so it is manual, like a functions deploy. CI runs
only the scorer and a record-and-replay round trip (`test/ai/eval-*.test.ts`).

## Setup

1. **Documents.** Licensed manuals are never committed; cases name them by sha256.
   - On a machine that has them: `npm run eval:add-doc -- <file> [--upload]` copies one into
     `eval/docs/` and prints its `documents` entry for `case.json`. `--upload` also copies it to
     `$EVAL_DOCS_BUCKET`.
   - Anywhere else: `EVAL_DOCS_BUCKET=gs://… npm run eval:fetch` restores every document the
     cases name, checking each sha256.
2. **Credentials.** Claude and Gemini run on Vertex AI through ADC
   (`gcloud auth application-default login`), with Claude enabled in Model Garden.
   - `VERTEX_PROJECT` (default `wingslog-9ca4e`), `VERTEX_LOCATION` (default `global`)
   - `ANTHROPIC_API_KEY` for `--claude=direct`, while the project has no Vertex quota for Claude
   - `OPENAI_API_KEY` for the OpenAI candidates
   - `DOCUMENT_AI_PROCESSOR=projects/…/locations/us/processors/…` (an Enterprise Document OCR
     processor) for image-only pages. Without it, those pages are not read.

## Running

```bash
npm run eval:tasks -- --fast=gemini-3.8-flash --strong=claude-sonnet-5-5
npm run eval:tasks -- --fast=… --strong=… --cases=triumph-t100-2024-handbook,triumph-t100-2024-no-docs
npm run eval:tasks -- --fast=… --strong=… --recall-tier=strong   # recall on the strong model
npm run eval:tasks -- --fast=… --strong=… --locate=model --repeat=3 --warm
npm run eval:tasks -- --replay=eval/out/<run>                    # re-score a run, free
```

| Flag | Default | Meaning |
|---|---|---|
| `--cases` | `all` | Comma-separated case ids |
| `--locate` | `keywords` | How schedule pages are found: `keywords`, `model` or `all` |
| `--recall-tier` | `fast` | Which model recalls the common schedule (design §6.3) |
| `--ocr` | `document-ai` | `none` to skip OCR |
| `--claude` | `vertex` | `direct` calls Claude on Anthropic's API with `ANTHROPIC_API_KEY`; same models, same list price |
| `--repeat` | `1` | Runs per case, for p90 latency |
| `--warm` | off | Re-runs each succeeded case on its warm cache, for R19's cache-hit time |
| `--replay` | — | Replays a run's recorded answers: no provider calls, no cost |

Each case runs on its own empty cache. The cache key does not name the provider, so a shared
cache would hand one model's answers to another.

A run writes `eval/out/<timestamp>_<fast>_<strong>/`: `report.md`, `results.json`, and per case
`result.json`, `score.json` and `recording.json`. `out/` is git-ignored; the bake-off PR adds the
chosen run's `report.md` and `results.json` with `git add -f`.

`npm run eval:review -- eval/out/<run> [eval/out/<run>...] [--index=eval/out/<name>.md]` writes a
readable `suggestions.md` into each run (every case's suggestions with schedule, first due,
source and what they matched), and with `--index` one page linking and comparing the runs.

`--replay` is the way to iterate on `expected.json` or the scorer: it re-runs the pipeline on the
recorded answers. A change to a prompt, schema or case context changes the requests, so the replay
stops with "nothing recorded" and the run has to be recorded again.

## Cases

`cases/<id>/case.json` holds `kind` (`document` or `no_document`) and the `request`: the Thing's
`context` as the client would build it (design §4.2, `src/ai/tasks/model.ts`) and its
`documents`. `expected.json` holds the answer:

```json
{
  "reviewed": false,
  "expectedStatus": "succeeded",
  "tasks": [
    {
      "titleAliases": ["spark plug replacement", "replace spark plugs"],
      "rules": [{ "kind": "meter", "meterKey": "engine_hours", "interval": 200 }],
      "citations": [{ "document": "rotax-mml-915i", "pages": [65] }],
      "type": "routine"
    },
    { "titleAliases": ["fuel filter"], "rules": [], "optional": true },
    { "titleAliases": ["crankcase AD"], "rules": [], "mustNotAppear": true }
  ]
}
```

- `titleAliases`: a suggestion matches when one alias's words are mostly in its title. The first
  alias names the task in the report.
- `rules`: as the Thing should carry them, in its own meter keys and units. Months and years
  compare as months; meter intervals within 2%. `[]` for an on-condition item.
- `alternativeRules`: other rule sets the manual allows when its interval depends on something
  the Thing does not record (a certified oil, severe service). Matching any counts as correct.
- `firstDueMeter`: for a one-time item, the reading it first falls due at, counted from new
  (`{ "meterKey": "odometer", "value": 600 }`). Its `rules` stay `[]` unless it also recurs.
- `citations`: where the item is stated, as the document's `blobId` (from `case.json`) and its
  **PDF page numbers** (the viewer's page count, not the number printed on the page). A task
  several documents state lists each; citing any one counts. Without citations, accuracy falls
  back to whether the cited page states the item. When the interval comes from a regulation
  rather than the documents, cite it as `{ "regulation": "14 CFR § 91.413" }`; that is for the
  reader and is not scored.
- `optional`: fine if suggested, not counted as missed. `mustNotAppear`: a hard failure if
  suggested.
- `reviewed`: flip to true once someone who knows the schedule has checked it. Aviation cases
  need A&P review before the airplane preset ships (PRD §9.3).
- `expectedStatus`: set it for a case that should end a particular way, e.g. `empty` for a
  Thing with only a name.

No-document cases are judged by hand (PRD §9.4). Their `tasks` may stay empty; the report
still checks the hard gates and shows every suggestion's source kind.

## Scoring

Per run: recall, interval accuracy and citation accuracy (document cases), invented items
(document-sourced suggestions matching no expected task), cost and latency. The hard gates are
checked here independently of the validators: AD/SB typing without that document, reference
numbers not verbatim, document suggestions their cited page does not state, meter keys outside
the template, and runs without valid output after one retry. `report.md` sets them against
PRD §9.4's bar.
