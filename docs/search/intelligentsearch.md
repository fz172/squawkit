# Intelligent Search Implementation for WingsLog

> **Status — Not yet implemented (research only).** No FTS5 tables or embedding/vector code exist in the
> codebase yet. This document remains forward-looking research; the local-first SQLDelight store
> (`core/storage`) is the foundation a future implementation would build on.

This document outlines the research and architectural strategy for implementing a tolerant,
"smart" search algorithm within WingsLog — a Kotlin Multiplatform (KMP) maintenance log
application using SQLDelight.

---

## 1. Overview of the Problem

Standard exact-match search algorithms fail to address three critical user needs in technical
logging:

- **Typo Tolerance:** Users often make small spelling errors (e.g., "Appel" instead of "Apple").
- **Grammatical Variation:** Phrases like "eat apple" should match "eat an apple."
- **Semantic Equivalence:** Technical jargon and acronyms (e.g., "XPDR" for "transponder") must
  be recognized as identical in meaning.

---

## 2. Recommended Technical Solutions

| Goal              | Technique                     | Implementation Method                                                                                  |
|-------------------|-------------------------------|--------------------------------------------------------------------------------------------------------|
| Typo Tolerance    | **FTS5 Trigram Tokenizer**    | Use SQLite's FTS5 extension to break text into 3-character chunks for fuzzy matching.                  |
| Semantic Matching | **Vector Embeddings**         | Convert text into mathematical vectors using local AI models (MediaPipe on Android, NLEmbedding on iOS). |
| Technical Jargon  | **Synonym Mapping**           | A shared Kotlin `Map` in `commonMain` to expand queries (e.g., mapping "XPDR" to "transponder").       |
| High Performance  | **Hybrid Search**             | Combining Keyword (FTS5) and Semantic (Vector) results using Reciprocal Rank Fusion (RRF).             |

---

## 3. Technical Implementation Deep-Dive

### 3.1 The KMP Dependency Path (The SQLite Challenge)

The standard `androidx.sqlite` bundled driver provides a consistent binary but does **not** include
the `sqlite-vector` (or `vss`) extension by default. To implement this in KMP, there are two
primary paths:

- **Custom SQLite Bundle:** Compile a custom SQLite binary that includes the `vss` extension and
  link it to your SQLDelight driver via `sqlite3_auto_extension()`. This is high effort but
  provides maximum control.
- **Library Approach:** Use a library like `sqlitevss` or `sqlite-vec`. For KMP, write
  platform-specific wrappers (`Actual`/`Expect`) to load these shared libraries (`.so` on Android,
  `.dylib` on iOS) into the SQLDelight driver at runtime.

### 3.2 Hybrid Scoring: Reciprocal Rank Fusion (RRF)

Merging keyword results (FTS5) with semantic results (Vector) is non-trivial because their scores
are on different scales (BM25 vs. Cosine Similarity). The recommended strategy is
**Reciprocal Rank Fusion (RRF)**.

**The Algorithm:** For each result appearing in either list, calculate a unified score:

```
Score = 1 / (k + rank_keyword) + 1 / (k + rank_semantic)
```

Where `k = 60` is the standard constant. This prioritizes results that rank highly in *either*
list without requiring normalized raw scores — the key advantage of RRF over score-based merging.

### 3.3 The Embedding Pipeline & Lifecycle

To avoid UI jank, the embedding process must be decoupled from the main database transaction.

- **On-Save Strategy:** When a log is saved, it is written to a **Pending Index** table. A
  background Coroutine (Android `WorkManager` / iOS `BackgroundTasks`) picks it up, generates the
  vector, and updates the main `LogEntry` table.
- **Incremental Indexing:** On edit, the entry is marked `dirty`. The background worker re-runs
  the embedding only for that specific row, avoiding full re-indexing.
- **Error Handling:** If the model fails to load, the system must fall back to pure FTS5 keyword
  search. A `SearchMode` flag stored in `DataStore` reflects engine availability at runtime.

### 3.4 Cold Start & Model Management on Android

Since Play Feature Delivery is used to distribute the ~22MB `all-MiniLM-L6-v2` model, the model
will not be present on first launch.

- **Required UX:** If a search is attempted before the model is ready, display a specific UI state:
  `"Smart search unavailable, downloading model..."` with a progress indicator.
- **Fallback:** Until the model download completes, all search queries fall back to FTS5 + synonym
  expansion only. The `SearchMode` flag in `DataStore` governs this transition automatically.
- **iOS:** Uses the native `NLEmbedding` framework, which is built into the OS via the Apple
  Neural Engine (ANE). No app size impact and no cold-start delay.

---

## 4. Model Distribution & Performance

| Platform    | Implementation Details                                                                     | Distribution Strategy                                                        |
|-------------|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| **Android** | **MediaPipe Text Embedder** — optimized for mobile GPUs/TPUs. Model: `all-MiniLM-L6-v2`. | **Download on first use (~22MB)** via Google Play Feature Delivery.          |
| **iOS**     | Native **NLEmbedding** — leverages the Apple Neural Engine (ANE).                         | **Bundled (0MB)** — uses the built-in system model, no app size impact.      |

### Dimensionality Mismatch & Sync Policy

`all-MiniLM-L6-v2` (Android) produces **384-dimension** vectors; `NLEmbedding` (iOS) produces
**512-dimension** vectors. These are fundamentally incompatible for comparison or cross-platform
KNN queries.

**Policy: Embedding databases must remain local-only and must never be synced between platforms.**
Only the source log text and metadata should be synced (if cloud sync is implemented). Each
platform re-indexes locally from the source text using its own model.

---

## 5. Database Strategy with SQLDelight

Since SQLDelight wraps SQLite, vector search is implemented via the `sqlite-vec` extension. This
allows for:

1. Storing log embeddings as `BLOB` types.
2. Executing K-Nearest Neighbor (KNN) searches within `.sq` files.
3. Maintaining offline-first capability, essential for maintenance environments with limited
   connectivity.

### KNN Query Workaround

SQLDelight's code generation does **not** natively support custom functions like
`vec_distance_cosine`. KNN queries must use a raw query workaround:

```sql
-- Example KNN search in a raw SQLDelight query
SELECT id, description
FROM log_entries
WHERE vec_distance_cosine(embedding, ?) < 0.3
ORDER BY vec_distance_cosine(embedding, ?)
LIMIT 10;
```

Register `vec_distance_cosine` as a custom function on the SQLDelight driver at initialization,
before any queries run.

---

## 6. Aviation Synonym Map

The synonym map is the highest-impact, lowest-complexity layer — it catches the most real-world
query variations before semantic search is even needed.

**Ownership:** A hardcoded starter set lives in `commonMain` as a `Map<String, List<String>>`.
This ensures immediate utility on both platforms with no model dependency.

**Starter set (non-exhaustive):**

```kotlin
val aviationSynonyms: Map<String, List<String>> = mapOf(
    "xpdr"        to listOf("transponder"),
    "elt"         to listOf("emergency locator transmitter"),
    "aoa"         to listOf("angle of attack"),
    "tas"         to listOf("true airspeed"),
    "ias"         to listOf("indicated airspeed"),
    "alt"         to listOf("altimeter", "altitude"),
    "vor"         to listOf("vhf omnidirectional range"),
    "ils"         to listOf("instrument landing system"),
    "mag"         to listOf("magneto"),
    "prop"        to listOf("propeller"),
    "carb"        to listOf("carburetor"),
    "eng"         to listOf("engine"),
    "mx"          to listOf("maintenance"),
    "squawk"      to listOf("transponder code"),
)
```

**Future extensibility:** The map should be designed to support remote config updates (e.g., a
bundled JSON file overridable via remote fetch) and optionally user-editable overrides stored in
`DataStore`.

---

## 7. Testing & Evaluation

Evaluating search quality requires more than code tests — it requires domain-specific recall
benchmarks.

- **Synthetic Ground Truth Dataset:** Create a `search_ground_truth.json` file containing query →
  expected log ID mappings. Example:
  ```json
  [
    { "query": "prop strike",       "expected_id": 402 },
    { "query": "xpdr not working",  "expected_id": 87  },
    { "query": "magneto check",     "expected_id": 210 }
  ]
  ```
- **Recall Testing:** Run the algorithm against this dataset in JUnit (Android) / XCTest (iOS)
  suites. Assert that the expected ID appears in the top-N results.
- **Regression Guard:** As RRF weights and synonym maps are tuned over time, the ground truth
  suite ensures changes don't silently degrade search quality.

---

## 8. Architecture Summary

```
SearchManager (commonMain)
├── Query Normalization
├── Synonym Expansion (aviationSynonyms map)
├── expect EmbeddingEngine (commonMain interface)
│   ├── actual MediaPipeEmbeddingEngine (androidMain)
│   └── actual NLEmbeddingEngine (iosMain)
├── FTS5 Keyword Search (SQLDelight)
├── KNN Vector Search (sqlite-vec, raw queries)
├── RRF Score Merger
└── SearchMode flag (DataStore) — degrades gracefully to FTS5-only
```

---

*Revised Technical Specification — May 9, 2026*
