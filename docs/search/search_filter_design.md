# Design Doc: Search and Filter

**PRD:** `docs/search/search_filter_PRD.md`
**Status:** 📋 Proposed
**Last updated:** 2026-09-05

---

## 1. Overview

Three tabs, one bar, one matcher. The Logs tab already owns a `LogFilter`, a search field, a
component sheet and active chips inside `feature/logs/viewing`. This design lifts that pattern out
into shared code, adds a time window and a tolerant matcher, and wires the same bar into the Squawks
and Tasks tabs rendered by `feature/thing/dashboard`.

Two new pieces of shared code:

- **`core/search`** — pure Kotlin, `commonMain` only, no Compose, no Koin. The filter model, the
  time-window semantics, the tokenizer, stemmer, synonym packs, edit-distance matcher, scorer and
  the `SearchHit` result with its explanation. Fully unit-testable on the JVM.
- **`core/ui`** additions — `RecordFilterBar`, `RecordFilterSheet`, `ActiveFilterChip`,
  `MatchExplanationRow`. Stateless composables driven by a `RecordFilter` value and callbacks.

State stays where each list already lives: `MaintenanceLogListViewModel` for logs,
`ThingOverviewViewModel` for squawks and tasks. Nothing new touches Firestore, the sync engine or
the schema. One small model addition (`DueMetadata.compliedDate`) is called out in §5.3.

---

## 2. Module Layout

```
core/search/                                   NEW  (pure Kotlin)
  src/commonMain/kotlin/dev/fanfly/wingslog/core/search/
    RecordFilter.kt          RecordFilter, TimeWindow, Facet
    RecordAdapter.kt         how a kind exposes its searchable fields, component and dates
    SearchEngine.kt          search(items, adapter, filter, today) -> List<SearchHit<T>>
    SearchHit.kt             SearchHit<T>, MatchExplanation
    text/Tokenizer.kt        normalise + tokenise, keeps whole serial/reference tokens
    text/Stemmer.kt          the dozen suffix rules
    text/EditDistance.kt     Damerau–Levenshtein with a cap
    synonyms/SynonymPack.kt  SynonymPack, GenericSynonyms, AviationSynonyms, packFor(templateId)
  src/androidHostTest/...    tokenizer, stemmer, distance, engine, ground-truth suite

core/ui/                                       EXTENDED
  common/compose/RecordFilterBar.kt
  common/compose/RecordFilterSheet.kt
  common/compose/ActiveFilterChip.kt           moved from feature/logs/viewing
  common/compose/MatchExplanationRow.kt

feature/logs/viewing/                          CHANGED  LogFilter -> RecordFilter; sheet removed
feature/thing/dashboard/                       CHANGED  squawk + task filters in the VM; bar in both tabs
feature/tasks/model/                           CHANGED  DueMetadata.compliedDate (§5.3)
feature/tasks/datamanager/                     CHANGED  TaskDueManager fills compliedDate
```

Dependency direction: `feature/*` → `core/ui` → `core/search` → `core/model`. `core/search` needs
`core/model` for `ComponentType` and the record types used by the adapters; the adapters themselves
live in `core/search` so the three ViewModels share them.

`core/search` is registered like every other core module: `settings.gradle.kts` include and the
`core/di` wiring is not needed because the engine is a plain class the ViewModels construct.

---

## 3. The Filter Model (`core/search`)

```kotlin
data class RecordFilter(
  val query: String = "",
  val components: Set<ComponentType> = emptySet(),
  val time: TimeWindow = TimeWindow.All,
  val facet: Facet? = null,
) {
  val isActive: Boolean get() = query.isNotBlank() || hasNonQueryFilter
  val hasNonQueryFilter: Boolean get() = components.isNotEmpty() || time != TimeWindow.All || facet != null
}

sealed interface TimeWindow {
  data object All : TimeWindow
  data class LastMonths(val months: Int) : TimeWindow          // presets: 3, 12
  data class Custom(val start: LocalDate, val end: LocalDate) : TimeWindow
}

/** The one tab-specific choice a sheet offers. Exactly one kind per tab. */
sealed interface Facet {
  data class Priority(val value: SquawkPriority) : Facet
  data class Compliance(val value: ComplianceType) : Facet
  data class Technician(val name: String) : Facet
}
```

`RecordFilter` is a value; the ViewModels hold it in a `MutableStateFlow` and expose it on their UI
state, exactly as `LogFilter` is handled today. `LogFilter` is deleted and its two call sites move to
`RecordFilter`.

### 3.1 Time-window semantics

`TimeWindow` only knows how to test a date. **Which** date a record contributes is the adapter's
decision, and it depends on the sub-view:

| Kind | Sub-view | Date tested | Direction |
|---|---|---|---|
| Log | — | `timestamp` | past |
| Squawk | Open | `created_at` | past |
| Squawk | Closed | `dismissed_at`, else the addressing log's timestamp, else `created_at` | past |
| Task | Active | `DueMetadata.nextDueDate` | **future** ("due within") |
| Task | Complied | `DueMetadata.compliedDate` (§5.3) | past |

```kotlin
enum class TimeDirection { PAST, FUTURE }

fun TimeWindow.contains(date: LocalDate?, today: LocalDate, direction: TimeDirection): Boolean =
  when (this) {
    TimeWindow.All -> true
    is LastMonths -> date != null && when (direction) {
      PAST -> date >= today.minus(months, MONTH) && date <= today
      FUTURE -> date >= today && date <= today.plus(months, MONTH)
    }
    is Custom -> date != null && date in start..end
  }
```

A record with a null date under a non-`All` window: excluded for logs and squawks (they always
have a date), **included** for active tasks (PRD FR.19: a meter-only task has no date and time
cannot exclude it). The adapter encodes this with `nullDateMatches`.

### 3.2 Record adapters

```kotlin
interface RecordAdapter<T> {
  fun fields(item: T): List<SearchField>            // ordered by weight, see §4.4
  fun component(item: T): ComponentType
  fun date(item: T, subView: SubView): LocalDate?
  val direction: (SubView) -> TimeDirection
  val nullDateMatches: Boolean
  fun facetMatches(item: T, facet: Facet): Boolean
}

data class SearchField(val name: String, val text: String, val weight: Int)

object SquawkAdapter : RecordAdapter<SquawkWithStatus>
object TaskAdapter : RecordAdapter<MaintenanceTaskWithStatus>
object LogAdapter : RecordAdapter<MaintenanceLog>
```

Fields per adapter (PRD FR.9), with weights:

| Adapter | Weight 4 | Weight 3 | Weight 1 |
|---|---|---|---|
| Squawk | `component_serial` | `title` | `description` |
| Task | `reference_number` | `title` | `notes`, `compliance_authority`, `compliance_details` |
| Log | `component_serial` | — | `work_description`, `technician.name` |

---

## 4. The Matcher (`core/search`)

Everything runs in memory over the list the tab already holds. A thing's logbook is hundreds of
records, rarely thousands; a full scan per keystroke is well under a millisecond of work on every
target, including Kotlin/JS. No index is stored, nothing is synced, nothing is platform-specific.

### 4.1 Normalise and tokenise

- Lowercase, NFD-decompose and strip combining marks.
- Split on anything that is not a letter, digit, `.`, `/` or `-`.
- A token containing `.`, `/` or `-` is kept whole **and** split into its parts, so `91.413`
  yields `91.413`, `91`, `413` and `AD 2011-10-09` still matches `2011-10-09` typed alone.
- A token is *numeric-ish* when it contains a digit. Numeric-ish tokens are never stemmed and never
  fuzzily matched (PRD FR.11).

### 4.2 Stem

A dozen suffix rules, applied to alphabetic tokens only: `-ing` (length > 5), `-ed` (length > 4),
`-s` (length > 3, not `-ss`), `-es` after `x`, `s`, `ch`, `sh`. That covers `mags`, `leaking`,
`replaced`, `inspections`. Nothing heavier: a full Porter stemmer over-stems technical vocabulary
(`bulletin` → `bulletin`, fine; `magneto` → `magnet`, wrong).

### 4.3 Expand synonyms

```kotlin
class SynonymPack(private val map: Map<String, List<String>>) {
  private val reverse: Map<String, List<String>>   // built once, single-word targets only
  fun expansions(token: String): List<String>       // map[token] + reverse[token]
  operator fun plus(other: SynonymPack): SynonymPack
}

val GenericSynonyms = SynonymPack(mapOf(
  "inop" to listOf("inoperative", "not working"),
  "u/s" to listOf("unserviceable"),
  "batt" to listOf("battery"),
  "mx" to listOf("maintenance"),
  "replc" to listOf("replaced"),
))

val AviationSynonyms = SynonymPack(mapOf(
  "xpdr" to listOf("transponder"),
  "elt" to listOf("emergency locator transmitter"),
  "mag" to listOf("magneto"),
  "prop" to listOf("propeller"),
  "carb" to listOf("carburetor"),
  "alt" to listOf("altimeter", "alternator"),
  "ad" to listOf("airworthiness directive"),
  "sb" to listOf("service bulletin"),
  "aog" to listOf("aircraft on ground"),
  "ia" to listOf("inspection authorization"),
  "tso" to listOf("time since overhaul"),
  "smoh" to listOf("since major overhaul"),
  // ... the starter set from intelligentsearch.md §6, reviewed
))

fun packFor(templateId: String?): SynonymPack =
  if (templateId == null || templateId == AirplaneTemplate.ID) GenericSynonyms + AviationSynonyms
  else GenericSynonyms
```

Expansion happens at **query time only**. A single-word expansion joins the token's OR-group; a
multi-word expansion is tested as a phrase against the field's normalised text. Null template counts
as aviation, for the same reason `usesComponentTypes` treats it so.

### 4.4 Match one query token against one field

For each query token `q` and each field, the best of:

| Step | Condition | Grade |
|---|---|---|
| Exact | a field token equals `q` | 1.00 |
| Stem | `stem(fieldToken) == stem(q)` | 0.95 |
| Prefix | `q.length ≥ 3` and a field token starts with `q` | 0.80 |
| Synonym | a field token (or phrase) equals an expansion of `q` | 0.70 |
| Fuzzy | `q` alphabetic, `q.length ≥ 4`, a field token of length ≥ 4 within `cap` edits, where `cap = 1` below 8 letters and `2` from 8 | 0.50 |

Fuzzy matching runs against the field's tokens with a capped Damerau–Levenshtein (transpositions
count as one edit, so `trasnponder` is one edit from `transponder`). The cap lets the DP bail out
early, so the cost stays proportional to token length, not to the corpus.

### 4.5 Score a record

```
for each query token q:
  best = max over fields of (grade(q, field) × field.weight)
  if best == 0: record is not a result
  score += best
tie-break: most recent date first (adapter date, sub-view aware)
```

Every query token must land somewhere (AND across tokens, PRD FR.12). Duplicated query tokens are
collapsed first.

### 4.6 Explain

```kotlin
data class SearchHit<T>(val item: T, val score: Double, val explanations: List<MatchExplanation>)

sealed interface MatchExplanation {
  data class Synonym(val query: String, val matched: String) : MatchExplanation
  data class Fuzzy(val query: String, val matched: String) : MatchExplanation
  data class Prefix(val query: String, val matched: String) : MatchExplanation
}
```

Only non-exact, non-stem steps produce an explanation. The card renders the first one, preferring
Synonym over Fuzzy over Prefix, as "Matched ‹matched› for ‹query›" / "Close to ‹matched›" /
"Starts with ‹query›" (PRD FR.13).

### 4.7 The engine

```kotlin
class SearchEngine(private val synonyms: SynonymPack) {
  fun <T> search(
    items: List<T>,
    adapter: RecordAdapter<T>,
    filter: RecordFilter,
    subView: SubView,
    today: LocalDate,
  ): List<SearchHit<T>>
}
```

`search` applies, in order: component filter, time window, facet, then the query. With a blank
query every surviving item is a hit with an empty explanation list and the caller's own ordering is
preserved (the tabs already sort squawks by priority and tasks by due status; search ranking must not
fight that when there is no query).

### 4.8 Threading

The ViewModels debounce the query by 150 ms and run `search` on `Dispatchers.Default`. Filter
changes other than typing are applied without debounce. Web has one thread; the work is small
enough that this is fine, and the debounce keeps keystrokes smooth.

---

## 5. State: Where the Filters Live

### 5.1 Logs — `MaintenanceLogListViewModel` (`feature/logs/viewing`)

Already the pattern. Changes:

- `_filter: MutableStateFlow<LogFilter>` → `MutableStateFlow<RecordFilter>`.
- The inline `sorted.filter { component && contains }` becomes one call to
  `SearchEngine.search(logs, LogAdapter, filter, SubView.Default, today)`.
- `Success.logs: List<MaintenanceLog>` → `Success.hits: List<SearchHit<MaintenanceLog>>` so the card
  can render the explanation. `totalCount` stays.
- New intents: `onTimeWindowChange`, `onFacetChange`. `onComponentFilterToggle` and `clearFilter`
  keep their names.
- The engine is built once in `init` from `packFor(template.id)`; the VM already has the thing id
  and can observe the template through the manager it uses today.

### 5.2 Squawks and Tasks — `ThingOverviewViewModel` (`feature/thing/dashboard`)

The VM already holds `squawks`, `activeTasks` and `completedTasks`. It gains two filter flows and
folds them into the existing combine:

```kotlin
private val _squawkFilter = MutableStateFlow(RecordFilter())
private val _taskFilter = MutableStateFlow(RecordFilter())

// ThingOverviewUiState.Success gains:
val squawkFilter: RecordFilter = RecordFilter(),
val taskFilter: RecordFilter = RecordFilter(),
val squawkHits: List<SearchHit<SquawkWithStatus>> = emptyList(),      // filtered, both statuses
val activeTaskHits: List<SearchHit<MaintenanceTaskWithStatus>> = emptyList(),
val completedTaskHits: List<SearchHit<MaintenanceTaskWithStatus>> = emptyList(),
```

The raw lists stay on `Success` untouched because the Overview tab, the AOG banner and the
starter-pack gate read them. `SquawkTab` and `MaintenanceTasksTab` switch to the `*Hits` lists and
keep their `rememberSaveable` Open/Closed and Active/Complied toggles: the toggle is a view
choice, not a filter, and the VM does not need to know it. The one wrinkle is the closed-squawk date
(§3.1): the VM runs the search twice for squawks, once per sub-view, or the adapter takes the status
from `SquawkWithStatus` and picks the date itself. The adapter route is simpler and is the design.

**Addressed-squawk date.** `Squawk.addressed_by_log_id` names the log but the VM does not load
every log. Cheapest correct source: `MaintenanceLogManager.observeLogDates(thingId): Flow<Map<String,
Instant>>`, a projection the store can answer without payload decoding. Until that exists, the
adapter falls back to `created_at` for addressed squawks, which the PRD lists as an open question.

New intents on `ThingOverviewAction`: `SquawkFilterChange(RecordFilter)`, `TaskFilterChange(RecordFilter)`.
One action per tab carrying the whole value keeps the action set small; the bar's callbacks compose
the new value from the current one.

### 5.3 `DueMetadata.compliedDate` (`feature/tasks/model`)

`DueMetadata` has no date for a complied task. `TaskDueManager` knows it (the complying log's
timestamp, or `ForceCompliedStatus.complied_date`). Add:

```kotlin
data class DueMetadata(
  ...,
  /** When the task was last complied, for the Complied sub-view's time filter. Null when never. */
  val compliedDate: LocalDate? = null,
)
```

Filled in `TaskDueManagerImpl` where the status is computed. Additive; no serialisation impact.
This is also the anchor "since last annual" would need (PRD open question 1).

### 5.4 Reset on thing switch

Both ViewModels are keyed by `thingId` at the composition site (`koinViewModel(key = thingId)` for
logs; the overview VM is likewise per thing). A new thing is a new VM with a fresh `RecordFilter()`,
which is PRD FR.7 for free. Rotation and tab switches keep the VM, so the filter survives.

### 5.5 Jump targets

`MaintenanceLogListContent` already clears the filter when `scrollToLogId` arrives and a filter is
active. The same `LaunchedEffect` moves into a small shared helper and is added to `SquawkTab` and
`MaintenanceTasksTab` next to their existing scroll-to logic. The `JumpTargetHighlight` wash is
unchanged.

---

## 6. UI (`core/ui`)

### 6.1 `RecordFilterBar`

```kotlin
@Composable
fun RecordFilterBar(
  filter: RecordFilter,
  placeholder: String,                 // "Search ‹noun›" from the lexicon
  showComponentFilter: Boolean,        // componentTypesApply
  onQueryChange: (String) -> Unit,
  onOpenFilters: () -> Unit,
  onRemoveComponent: (ComponentType) -> Unit,
  onClearTime: () -> Unit,
  onClearFacet: () -> Unit,
  facetLabel: (Facet) -> String,
  modifier: Modifier = Modifier,
)
```

Renders the search row (field + filter button, indicator when `hasNonQueryFilter`) and the chips
row. It is what `MaintenanceLogListContent` lines 221–305 do today, generalised. The count row is a
separate tiny composable, `RecordCountRow(shown, total, noun, onClear)`, because the tabs place it
differently relative to their status toggle.

### 6.2 `RecordFilterSheet`

```kotlin
@Composable
fun RecordFilterSheet(
  title: String,
  filter: RecordFilter,
  showComponentFilter: Boolean,
  timeLabel: String,                   // "Period" or "Due within"
  timeNote: String?,                   // FR.19 line on the Tasks tab
  onComponentToggle: (ComponentType) -> Unit,
  onTimeWindowChange: (TimeWindow) -> Unit,
  facetSection: @Composable ColumnScope.() -> Unit,   // the tab-specific chips
  onClear: () -> Unit,
  onDismiss: () -> Unit,
)
```

Built on `ModalBottomSheet` from `core.ui.common.compose`, so the popup selection-scope rule is
satisfied by construction (`checkPopupSelectionScopes` will pass). Choosing `Custom` reveals two
fields that open `DatePickerDialog` from the same package. `Clear` resets to `RecordFilter()`
keeping the query; `Done` dismisses.

### 6.3 `ActiveFilterChip`, `MatchExplanationRow`

`ActiveFilterChip` moves from `feature/logs/viewing` unchanged. `MatchExplanationRow(explanation)` is
one row: the spark icon, the sentence, tertiary colour (Advisory Amber, within the ≤10% rule
because it appears only on non-exact hits). Card composables in `feature/squawk`, `feature/tasks`
and `feature/logs` gain an optional `explanation: MatchExplanation?` parameter and render the row
after their metadata line.

### 6.4 Strings

Shared labels move to `core/ui` resources: filter title, "Applies to this tab only", Period, Due
within, All time, Last 3 months, Last 12 months, Custom range, Clear, Clear filters, the FR.19
note, "N of M", and the three explanation formats. `search_logs`, `filter_by_type`,
`no_logs_match_filter` and `clear_filter` in `feature/logs/viewing` are retired in favour of
lexicon-formatted equivalents. Squawk priority and compliance-type labels already exist in their
feature `sharedassets` and are reused.

### 6.5 Web keyboard

`/` focuses the visible tab's search field, `Esc` clears it. Implemented as a `Modifier.onPreviewKeyEvent`
on the shell content in `feature/shell`, forwarding to a `FocusRequester` the bar exposes. Compact
tiers ignore it.

---

## 7. Analytics

Two typed events in `core/analytics`:

| Event | Params |
|---|---|
| `record_filter_applied` | `tab` (squawks/tasks/logs), `kind` (component/time/facet/clear), `value` (component name, preset name or `custom`, facet kind) |
| `record_search` | `tab`, `query_len` bucket (1–3, 4–8, 9+), `results` bucket (0, 1–5, 6+), `explained` (true when the top hit carried an explanation) |

Never the query text. Fired from the ViewModels, on the debounced query and on each filter change.

---

## 8. Testing

**`core/search` (JVM, JUnit 4 + Truth):**

- Tokenizer: serials and references kept whole and split; accents folded; numeric-ish detection.
- Stemmer: the rule table, and a guard that `magneto`, `bulletin`, `annual` are untouched.
- Edit distance: transposition counts one; cap bails out; numeric tokens never fuzz.
- Engine: AND across tokens; ranking order exact > stem > prefix > synonym > fuzzy; field weight
  beats grade where intended (`91.413` in `reference_number` outranks the same in `notes`);
  blank query preserves input order; explanation selection.
- Time window: every row of the §3.1 table, with a fixed `today`; null-date handling per adapter.
- **Ground-truth suite:** a Kotlin list of `(query, expectedId)` over a fixture logbook, asserting
  the expected record is in the top 5. Three families: typos, acronyms both directions, exact
  serial and reference lookups. This is the regression guard for every later synonym or weight
  change.

**ViewModels (existing test modules):**

- `MaintenanceLogListViewModel`: filter intents update state; jump target clears filter.
- `ThingOverviewViewModel`: `squawkHits` and task hits respond to their filters; raw lists are
  untouched by a filter; a new thing id yields a clean filter.
- `TaskDueManagerImpl`: `compliedDate` is set from the complying log and from force-complied.

**Lint:** `checkPopupSelectionScopes` covers the new sheet.

---

## 9. Migration of the Logs Tab

1. Add `core/search` with `RecordFilter` and a `SearchEngine` whose query step is plain substring
   (P1), swapped for §4 in P2.
2. Replace `LogFilter` with `RecordFilter` in `MaintenanceLogListUiState` and the VM.
3. Move `ActiveFilterChip` to `core/ui`; replace the inline search row and `FilterSheetContent`
   with `RecordFilterBar` and `RecordFilterSheet`.
4. Delete the retired strings.

The tab looks the same after step 3 except for the new Period section in the sheet. That is the
checkpoint before touching Squawks and Tasks.

---

## 10. Implementation Order

| Step | Modules | Phase |
|---|---|---|
| 1 | `core/search`: model, time window, adapters, substring engine, tests | P1 |
| 2 | `core/ui`: bar, sheet, chip, count row, strings | P1 |
| 3 | Logs tab migration (§9) | P1 |
| 4 | `DueMetadata.compliedDate` + `TaskDueManagerImpl` | P1 |
| 5 | `ThingOverviewViewModel` filters and hits; `SquawkTab`, `MaintenanceTasksTab` bars; jump clearing | P1 |
| 6 | Tokenizer, stemmer, synonym packs, distance, scorer, explanations, ground-truth suite | P2 |
| 7 | `MatchExplanationRow` on the three card composables | P2 |
| 8 | Facets (priority, compliance, technician) in sheet and adapters | P3 |
| 9 | Web keyboard, analytics events | P3 |
| 10 | Post-task cleanup pass, `graphify update .`, AGENTS.md status note | each phase |

---

## 11. Relationship to `intelligentsearch.md`

That document proposed FTS5 trigram tables, on-device embedding models and rank fusion. This design
replaces it for the foreseeable corpus sizes: an in-memory matcher in `commonMain` delivers the
typo tolerance and acronym expansion it was after, runs on web where FTS5 and the embedding models
are unavailable, and adds no download or native dependency. The `SearchEngine` interface is the seam:
if a thing ever carries tens of thousands of records, an FTS5-backed engine on Android and iOS can
implement the same `search` behind the same bar. `intelligentsearch.md` stays as the record of that
escalation path and of the synonym starter set.

---

## 12. Open Questions

1. `observeLogDates` projection versus loading logs in the overview VM for the addressed-squawk
   date. The projection is cleaner; confirm `EntityStore` can serve it from envelope data.
2. Whether `Custom` should remember its last range within the session when switching back from a
   preset. Proposed: yes, cheap, keep it in the VM next to the filter.
3. Technician facet source: names from the logs on this thing only, or the technician roster the
   sharing manager exposes. Proposed: logs only, it is what the list can actually show.
