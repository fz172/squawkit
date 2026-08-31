# Canonical templates

The source of truth for each canonical preset, and the compiled bytes the app bakes in.

```
airplane.v1.textproto   authored, reviewed, explained — edit this
airplane.v1.pb          compiled from it — committed, never hand-edited
compile-template.sh     the compiler
```

## Editing a template

```bash
./compile-template.sh airplane.v1        # regenerates airplane.v1.pb
./gradlew :core:template:testAndroidHostTest
```

Both steps. `AirplaneTemplateAssetTest` is what tells you the `.pb` is stale, and it is wired into
Gradle's inputs so it re-runs whenever a `.pb` changes.

> **A published template is never edited.** `(id, version)` has to always name the same bytes
> (`template_system_design.md` §5), so a correction after publication is a new `version` — a new
> pair of files — not a change to these. Editing in place changes what an already-distributed
> version means, and clients that cached the old bytes never find out.

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
| `theStructureTheAirplaneScreensAssumeIsIntact` | a dropped identifier flag, a reordered section, a meter on the wrong slot |
| `TemplateKeysResolveTest` | a slot or spec key the app emits that the template does not declare |
| `StringSnapshotTest` | any rendered string drifting from what the app shipped |

The content assertions are deliberate duplication: `.textproto` → `.pb` → embedded constant is
self-consistent, so every structural check compares one link against another and a wrong value at
the source passes all of them. Confirmed by flipping a byte in the `.pb` and watching the suite go
green until those assertions existed.
