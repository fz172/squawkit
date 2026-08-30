# Phase 2C string classification (#655)

The bucket assignment for all 968 strings, as a reviewable artifact rather than a side effect of editing.

**Why this is a deliverable.** A string in the wrong bucket is not caught by the byte-identical snapshot test
(#658). An unconverted string stays correct for aviation forever and is silently wrong for every other preset —
it only surfaces when someone adds a boat, long after the phase that introduced it. The test proves the strings
we *did* convert still render identically; nothing proves we converted the right set. That is what this is for.

- **`string_classification.tsv`** — every string, its bucket, the lexicon slots it matches, and why.
- **`classify_strings.py`** — the first-pass classifier. Re-runnable; regenerate after any `strings.xml` change.

## Result

| Bucket | Count | Share |
|---|---:|---:|
| **Neutral** — no domain vocabulary, untouched | 745 | 76% |
| **Substitutable** — a lexicon noun fills a format string | 189 | 19% |
| **Structural** — cannot be neutralised by substitution | 34 | 3% |
| | **968** | |
| *Name the domain* | **223** | **23%** |

PRD §10 estimated ~230 of 982 naming the domain. **223 of 968 — the estimate held**, which is worth knowing
because the corpus grew ~160 entries between PRD drafts and the ratio was the thing claimed to be stable.

## The finding that changes #657

§10 and #657 both describe the structural bucket as strings that "move into the lexicon." **Most of them do
not.** Of the 34:

| Destination | Count | Examples |
|---|---:|---|
| **`MeterDef.label` / `unit_label`** | ~16 | `schedule_track_tach_hours`, `prop_time_label`, `engine_hours_upper`, every `adj_preview_*_hours` |
| **Capability-gated, not substituted** | ~6 | `certificate_type_amt`, `no_certificate` — removed wholesale by `technician_certificates` (§4.8), never reworded |
| **`ComponentSlot.label`** | ~4 | `component_propeller`, `propeller_hub` |
| **Genuinely `Lexicon`** | ~5 | `compliance_type_ad`, `aog_alert_*` |
| **Marketing copy** | ~3 | `no_fleet_title` ("Ready for Takeoff?"), `onboarding_welcome_tagline` ("…in a hangar") |

So the structural bucket is mostly **template field labels**, and the lexicon takes only a handful. #657 should
be re-scoped accordingly — and the marketing strings are a third thing again: per-template *copy*, not a label
or a noun, and the first real customer for the per-string override idea in `template_system_design.md` §10a.

## What the classifier is, and is not

A keyword matcher over resource name and value. It is a **first pass**: every row still needs a human read,
because the interesting cases are ones where a domain word appears and the string is not what it looks like.

Three systematic errors were found and fixed while building it, each of which silently changed the totals:

1. **`"tach"` matched inside `"attach"`.** Every attachment string landed in the aviation bucket. Fixed with
   word boundaries — and it is why the first run reported 61 structural strings instead of 34.
2. **`\b` treats `_` as a word character**, so `\baircraft\b` never matched the resource *name*
   `aircraft_shared_badge`, and `\bsquawk\b` missed the value `"Squawks"`. Fixed by flattening underscores and
   allowing a plural `s`.
3. **`app_name = "SquawkIt"` classified as substitutable.** The product name contains a lexicon word and must
   never be substituted — a boat owner's app is still called SquawkIt. Brand mentions are now stripped before
   matching.

Each was found by sampling the output, not by the classifier failing. **Do not trust a run that has not been
spot-checked in both directions** — false positives in the domain buckets and false negatives in neutral.

### Known judgement calls left open

- **`"log"` alone is not a slot word.** Too common — `login`, `logout`, `logging`, `firebase_logging_subtitle`.
  So `addressed_by` ("Addressed by log") sits in neutral and needs a human decision. Matching it automatically
  would drag in a dozen unrelated strings.
- **Developer surfaces are excluded wholesale** (`stress_test_*`, `developer_options_*`). They are user-visible
  only in debug builds and are not worth lexicon plumbing, but that is a judgement, not a fact.
- **Squawk-lifecycle strings that never say "squawk"** — `dismiss_reason_duplicate`, `closed_with_count` — are
  neutral by wording and stay that way. Correct today; worth re-reading if a preset ever needs different
  lifecycle vocabulary.
