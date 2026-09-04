# Canonical templates

The source of truth for each canonical preset, and the compiled bytes the app bakes in.

```
*.textproto             authored, reviewed, explained — edit these
binary/*.pb             compiled from them — committed, never hand-edited
compile-template.sh     the compiler
```

Six presets ship: `airplane`, `automotive`, `bike`, `boat`, `home`, `custom`. `automotive` covers
cars and motorcycles together — PRD §4.8 lists them as separate rows and is stale on that point.
Two of the six carry more weight than the rest. **`home` is load-bearing** — no make, no model, no serial, no component
slots, an empty meter list — so it is the one that finds screens with an aviation assumption baked
in. **`custom` is the floor**: it declares almost nothing, so a screen that breaks on it is reading
something no template promises.

Every preset but `custom` ends with a `starter_tasks` block — the schedule offered when a Thing is
created and again from an empty Tasks tab (PRD §4.9). `CanonicalTemplatesTest` holds each item to
what the task form could produce: a title, a description, at least one rule, and a meter or slot
the preset declares. The airplane pack is pinned by content to regulatory-universal intervals.

## Editing a template

**Every edit is a new version.** Copy the file to the next `vN`, change `version:` inside it to
match, delete the old `.pb`, and compile the new one:

```bash
git mv airplane.v4.textproto airplane.v5.textproto   # and set `version: 5` inside
git rm binary/airplane.v4.pb
./compile-template.sh airplane.v5                    # writes binary/airplane.v5.pb
./gradlew :core:template:testAndroidHostTest
```

Nothing else changes: Gradle keys the generated constant on the preset id and takes the highest
version it finds, so `AIRPLANE_BASE64` follows the rename on its own.

> **A version bump is not a merge conflict.** Two branches that each bump the same preset produce
> two different *files*, so git merges both cleanly and leaves them side by side — and because the
> generator takes the highest version, the later bump wins and the other branch's change vanishes
> with nothing failing. #781 and #783 did exactly this to `custom`. Rebase onto the other bump and
> fold both changes into one version; `TemplateAssetDirectoryTest` is what catches it if you do not.

Then move the Things already in the field onto it:

```bash
cd backend/firebase/functions && npm run dna-refresh -- --dry-run
```

Both steps. `AirplaneTemplateAssetTest` and `CanonicalTemplatesTest` are what tell you a `.pb` is stale or a
preset is invalid, and they are wired into Gradle's inputs so they re-run whenever a `.pb` changes.

> **A published template is never edited.** `(id, version)` has to always name the same bytes
> (`template_system_design.md` §5), so a correction after publication is a new `version` — a new
> pair of files — not a change to these. Editing in place changes what an already-distributed
> version means, and clients that cached the old bytes never find out.
>
> **This holds pre-release too, and airplane v1 is why.** It was edited in place four times on the
> reasoning that nothing fetches templates yet and the only copies live inside Thing DNA. What that
> actually produced was several populations of aeroplane all claiming `airplane v1` and all walking
> different slots — the #732 blade regression was one of them, an aeroplane rendering its blades
> from bytes frozen before `compact_instances` existed. A stored template is identified by its
> version; an unbumped edit makes that identification wrong, and no amount of "not released yet"
> changes it. Bump on **every** change, including a lexicon word.

## Why protoc runs here and not in Gradle

Wire owns codegen in this repo and has **no protobuf text-format parser** — it turns a `.proto`
*schema* into Kotlin and cannot read a `.textproto` *data* file. `protoc --encode` is the only
off-the-shelf tool that does text → binary, so protoc is needed purely as a data compiler, never
for codegen.

Putting it in the build would add a native binary download and an unzip to every developer's Gradle
configuration path, for an asset that changes about once per preset. Committing the `.pb` instead
means a fresh clone and CI need no protoc at all, and the publishing script (#725) needs protoc
regardless — so that is the one place it lives.

Two things that cost time when this was first set up, both worth knowing before you touch the
script:

- **The Maven `protoc` artifact is the bare binary.** It does not carry the `include/` directory the
  release zip ships. `thing/template.proto` does not import `Timestamp` itself, but protoc compiles
  the whole `--proto_path` and siblings do, so without the well-known types on a second proto path
  the encode fails on *their* imports — an error that reads as if `template.proto` were broken.
- **protoc and Wire encode repeated enums differently, and both are right.** proto3 defaults to
  packed, which protoc emits; Wire writes them unpacked, one tag per value. The messages are equal
  while the bytes differ (703 vs 707 for `airplane.v1`), so byte-equality against the committed
  `.pb` cannot be asserted, and anything fingerprinting a template must hash the published bytes
  rather than a local re-encode.

## What checks what

| Check | Catches |
|---|---|
| `theEmbeddedTemplateMatchesTheCommittedAsset` | the Gradle generator skipped, mis-wired, or stale |
| `theLexiconStillSaysWhatTheAppSays` | a wrong word at the source — the `.pb` is binary, so review will not |
| `theEmptyStateCopyStillSaysWhatTheAppSays` | aviation empty-state copy drifting — the only guard on it since it left `strings.xml` |
| `theStructureTheAirplaneScreensAssumeIsIntact` | a dropped identifier flag, a reordered section, a meter on the wrong slot |
| `CanonicalTemplatesTest` | the PRD §4.7 rules over every preset — duplicate keys, an empty noun, meters claimed but not declared, a meter scoped to a slot that does not exist |
| `TemplateKeysResolveTest` | a slot or spec key the app emits that the template does not declare |
| `StringSnapshotTest` | any rendered string drifting from what the app shipped |
| `TemplateAssetDirectoryTest` | two versions of one preset left on disk — the generator takes the highest and drops the other silently |

The content assertions are deliberate duplication: `.textproto` → `.pb` → embedded constant is
self-consistent, so every structural check compares one link against another and a wrong value at
the source passes all of them. Confirmed by flipping a byte in the `.pb` and watching the suite go
green until those assertions existed.
