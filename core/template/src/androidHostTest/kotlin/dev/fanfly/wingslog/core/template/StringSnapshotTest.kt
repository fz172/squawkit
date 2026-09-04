package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.Noun
import org.junit.Test
import java.io.File

/**
 * **Phase 2's whole safety argument** (#658, PRD §10, §14 "Dilution").
 *
 * Phase 2C converts ~189 strings from fixed text into format strings filled from a lexicon. The
 * claim that justifies doing that at all is that an aviation user's app stays *verbally identical*
 * — not "mostly the same". This holds that claim to account: a snapshot of every string as it read
 * **before** any conversion, compared against what the airplane lexicon renders after.
 *
 * ## It compares renders, not source
 *
 * `"Add aircraft"` becoming `"Add %1${'$'}s"` is the expected outcome of #656, not a regression, so
 * comparing `strings.xml` against the snapshot directly would fail on every correct conversion.
 * Instead each converted string is rendered with [AirplaneTemplate.AIRPLANE_LEXICON] and *that* is
 * compared. `"Add %1${'$'}s"` filled with the `thing` singular must produce exactly `"Add aircraft"`
 * again — including its case, which is why the recipe names a formatter rather than just a slot.
 *
 * ## The snapshot was captured before the conversion, deliberately
 *
 * A snapshot taken from converted code asserts only that the code matches itself. `string_snapshot
 * .tsv` was generated while every string was still fixed text, so it is independent evidence of
 * what the app said rather than a restatement of what it says.
 *
 * ## A label that *is* a lexicon value needs no string resource at all
 *
 * If the whole value would become `"%1${'$'}s"`, the resource carries no information — the lexicon
 * does. Those call sites read the formatter directly and the resource is deleted, rather than every
 * such label getting its own pass-through entry (or all of them sharing one, which is the same
 * indirection with fewer files). `LexiconFormatterTest` pins the rendered words instead.
 *
 * ## Converting a string requires declaring its recipe
 *
 * [LEXICON_ARGS] maps a resource to the lexicon-derived arguments that fill it. A string whose
 * value changed but which has no entry **fails the test** — so a conversion cannot quietly drop out
 * of coverage by being converted, which is the one way this guard could be defeated while still
 * looking green.
 *
 * ## It needs the Gradle input declaration in `build.gradle.kts`
 *
 * This reads `strings.xml` from the filesystem, which Gradle does not track. Without the
 * `repoStringResources` input wired up in this module's build file, the task is UP-TO-DATE whenever
 * `core/template` itself is unchanged — so it would be skipped on exactly the commit that edits a
 * string elsewhere, and a skipped task reports success. Do not remove that block as dead
 * configuration; it is what makes this test run at all.
 *
 * ## If this fails
 *
 * **Regenerating the snapshot is almost never the fix.** A failure means one of:
 *
 * - a conversion changed the rendered wording — a product change, revert it or justify it;
 * - a string was edited without intent — the interesting case, and the one this catches;
 * - a string was deliberately reworded — then update the snapshot **in the same commit as the
 *   wording change**, so review sees both halves together.
 *
 * The one thing that destroys its value is regenerating it to get to green. Nothing downstream
 * would notice.
 *
 * ## What it does not cover
 *
 * Recorded because a test that looks total and is not is worse than one whose limits are known:
 *
 * - **It renders every recipe with the airplane lexicon, wherever the string actually appears.**
 *   The SETTINGS section is provided the *generic* lexicon, so a settings string is checked here
 *   against words it will never be given. Today that is harmless because the two lexicons agree on
 *   every noun a settings string uses — the technician noun is "technician" in both — but the
 *   agreement is a fact about the current lexicons, not something this test enforces. The rule that
 *   keeps it safe lives in `AdaptiveAppShell`: a settings string is only a conversion candidate if
 *   its generic rendering is acceptable.
 * - **It checks the recipe, not the call site.** [LEXICON_ARGS] says `add_thing` is filled with
 *   `sentenceCase(thing)`; a call site passing `titleCase(thing)` instead still passes here. The
 *   two are written in the same commit, which is the mitigation, not a proof.
 * - **Two lexicon fields render nowhere today** — `ready_status` and `collection_label`. The app
 *   has no "Airworthy" string and shows "Fleet" only inside `no_fleet_title`, so nothing here can
 *   check them (see [AirplaneTemplate]).
 * - **OS notification channel names live outside the app.** `GROUNDED`'s display name is in system
 *   settings; this test cannot see it, which is why #663 pins the channel *id* separately.
 * - **It compares source, not pixels.** A string correct in `strings.xml` but passed to the wrong
 *   composable still renders wrongly, and this will pass.
 */
class StringSnapshotTest {

  /**
   * How the airplane lexicon fills each converted string, by argument position.
   *
   * Keyed `module:resource`, valued by the positional arguments the lexicon supplies — `3 to "…"`
   * fills `%3${'$'}s`. Positions the *caller* supplies at runtime (a thing's name, a count) are
   * deliberately absent and stay as literal placeholders on both sides of the comparison, because
   * the snapshot recorded them that way too.
   *
   * Each conversion adds its entry in the same commit that changes the string.
   */
  private val LEXICON_ARGS: Map<String, (Lexicon) -> Map<Int, String>> = mapOf(
    // feature/squawk/sharedassets (#656). Three of the module's domain strings are absent
    // deliberately: "Squawks", "AOG" and "Aircraft on Ground" are lexicon values in their
    // entirety rather than sentence frames, so they belong to #657's move-into-the-lexicon.
    squawkFrame("add_squawk") { it.singular },
    squawkFrame("edit_squawk") { it.singular },
    squawkFrame("no_open_squawks") { it.plural },
    squawkFrame("no_closed_squawks") { it.plural },
    // Position 2 is the log noun, added with the task-side conversion below — the sentence named
    // "this log" literally while its subject came from the lexicon.
    "feature/squawk/sharedassets:no_squawk_work_recorded" to { l: Lexicon ->
      mapOf(1 to l.squawkNoun.plural, 2 to l.logNoun.singular)
    },
    squawkFrame("view_squawks") { LexiconFormatter.titleCasePlural(it) },
    squawkFrame("squawk_added") { LexiconFormatter.sentenceCase(it) },
    squawkFrame("squawk_updated") { LexiconFormatter.sentenceCase(it) },
    squawkFrame("squawk_dismissed") { LexiconFormatter.sentenceCase(it) },
    squawkFrame("squawk_reopened") { LexiconFormatter.sentenceCase(it) },

    // feature/sharing/sharedassets (#656). Four of this module's domain strings are absent for the
    // same reason as the squawk module's: "Technician" (twice), "a technician" and "Work logs" are
    // whole lexicon values rather than frames, so they are #657's.
    sharing("enter_code_instructions") { mapOf(1 to it.thingNoun.singular) },
    sharing("enter_code_title") { mapOf(1 to it.thingNoun.singular) },
    sharing("invite_code_hint") { mapOf(1 to it.thingNoun.singular) },
    sharing("invite_title") { mapOf(1 to it.thingNoun.singular) },
    sharing("leave_confirm_title") { mapOf(1 to it.thingNoun.singular) },
    sharing("manage_access_leave") { mapOf(1 to it.thingNoun.singular) },
    sharing("manage_access_help_footer") { mapOf(1 to it.thingNoun.singular) },
    sharing("redeem_already_member_body") { mapOf(1 to it.thingNoun.singular) },
    sharing("revoke_confirm_body") { mapOf(1 to it.thingNoun.singular) },
    sharing("role_confirm_body") { mapOf(1 to it.thingNoun.singular) },
    sharing("leave_confirm_body") { mapOf(1 to LexiconFormatter.lowerFirst(it.collection_label)) },
    sharing("manage_access_perm_thing_details") {
      mapOf(1 to LexiconFormatter.sentenceCase(it.thingNoun))
    },
    sharing("redeem_confirm_title") {
      mapOf(
        1 to LexiconFormatter.sentenceCase(
          it.thingNoun
        )
      )
    },
    sharing("sharing_sync_off_body") {
      mapOf(1 to LexiconFormatter.sentenceCasePlural(it.thingNoun))
    },
    sharing("manage_access_perm_squawks_tasks") {
      // Only the squawk noun. "tasks" stays literal because the airplane task noun is
      // "maintenance tasks", and substituting it would reword the string to "Squawks and
      // maintenance tasks" — a product change wearing a refactor's clothes.
      mapOf(1 to LexiconFormatter.sentenceCasePlural(it.squawkNoun))
    },
    sharing("manage_access_empty_desc") {
      mapOf(1 to it.technicianNoun.singular, 2 to it.thingNoun.singular)
    },
    sharing("manage_access_role_co_owner_desc") {
      mapOf(1 to it.technicianNoun.singular, 2 to it.thingNoun.singular)
    },
    sharing("manage_access_solo_body") {
      // "mechanic" is left literal for the same reason: the technician noun is "technician".
      mapOf(1 to it.squawkNoun.plural, 2 to it.thingNoun.singular)
    },
    sharing("manage_access_role_technician_desc") {
      mapOf(
        1 to it.squawkNoun.plural,
        2 to it.taskNoun.plural,
        3 to it.logNoun.plural,
        4 to it.thingNoun.singular,
      )
    },
    sharing("redeem_confirm_body") {
      mapOf(
        1 to LexiconFormatter.withArticle(it.thingNoun),
        2 to LexiconFormatter.lowerFirst(it.collection_label),
      )
    },
    // Position 1 is the inviter's name, supplied by the caller, so the lexicon starts at 4.
    sharing("redeem_confirm_body_full") {
      mapOf(4 to LexiconFormatter.lowerFirst(it.collection_label))
    },
    sharing("redeem_confirm_body_role") {
      mapOf(
        2 to LexiconFormatter.withArticle(it.thingNoun),
        3 to LexiconFormatter.lowerFirst(it.collection_label),
      )
    },
    sharing("redeem_success_body") {
      mapOf(
        2 to it.thingNoun.singular,
        3 to LexiconFormatter.lowerFirst(it.collection_label),
      )
    },

    // The task surfaces (#656). All per-thing: a task belongs to one thing, so the selected
    // thing's words are the right words here. Absent on purpose — the four compliance_type_*
    // strings and "Maintenance Tasks" are whole lexicon values (#657), and compliance_notes_hint
    // is per-template *example copy*: "e.g. One-time inspection of fuel lines" has no noun to
    // substitute, it has to be rewritten per template (design §10a). no_tasks_yet_description and
    // no_complied_yet were the same case and are gone — they live in `Lexicon.empty_states` now,
    // pinned by `theEmptyStateCopyStillSaysWhatTheAppSays` instead of by a row here.
    // Three that were converted here first — link_to_task, task_identity_description,
    // affects_n_tasks — turned out to have no call site at all, and have been deleted along with
    // the other 63 dead strings. A recipe on a string nothing renders passes forever while testing
    // nothing, and neither guard can see it: the round-trip has no placeholder to fill, and the
    // bare-call check has no call to find.
    frame(
      "feature/tasks/update",
      "component_type_description"
    ) { it.componentNoun.singular },
    frame(
      "feature/tasks/update",
      "create_work_log"
    ) { LexiconFormatter.titleCase(it.logNoun) },
    frame(
      "feature/tasks/update",
      "no_tasks_configured"
    ) { it.thingNoun.singular },
    frame(
      "feature/tasks/update",
      "task_title"
    ) { LexiconFormatter.titleCase(it.taskNoun) },
    frame("feature/tasks/sharedassets", "no_tasks_yet") { it.taskNoun.plural },
    frame("feature/tasks/viewing", "maintenance_due_subtitle") {
      LexiconFormatter.sentenceCasePlural(it.taskNoun)
    },

    // The log surfaces (#656). Per-thing: a log belongs to one thing. Absent on purpose —
    // airframe_serial, airframe_time_hours and airframe_time_label are a component-slot label and
    // two meter labels, so they are template *fields* rather than lexicon nouns (#657).
    // no_maintenance_logs_description is gone rather than converted: its value is its EXAMPLES —
    // "oil change, annual, 100-hour" — and no noun substituted into that frame makes it a
    // homeowner's sentence. It moved to `Lexicon.empty_states`.
    frame(
      "feature/logs/update",
      "component_section_description"
    ) { it.thingNoun.singular },
    frame("feature/logs/update", "loading_thing") { it.thingNoun.singular },
    frame("feature/logs/update", "no_engines_found") { it.thingNoun.singular },
    frame(
      "feature/logs/update",
      "performed_by_description"
    ) { it.technicianNoun.singular },
    frame("feature/logs/update", "squawks_section_header") {
      LexiconFormatter.titleCasePlural(it.squawkNoun)
    },
    frame("feature/logs/update", "tasks_section_header") {
      LexiconFormatter.titleCasePlural(it.taskNoun)
    },
    frame("feature/logs/viewing", "affected_maintenance_tasks") {
      LexiconFormatter.titleCasePlural(it.taskNoun)
    },
    frame(
      "feature/logs/viewing",
      "thing_data"
    ) { LexiconFormatter.titleCase(it.thingNoun) },
    frame(
      "feature/logs/viewing",
      "log_squawk_count_one"
    ) { it.squawkNoun.singular },
    frame("feature/logs/viewing", "resolved_squawks") {
      LexiconFormatter.titleCasePlural(it.squawkNoun)
    },
    frame("feature/logs/sharedassets", "no_maintenance_logs_title") {
      LexiconFormatter.sentenceCase(it.logNoun)
    },
    frame("feature/logs/sharedassets", "add_log") {
      LexiconFormatter.titleCase(
        it.logNoun
      )
    },
    frame("feature/logs/sharedassets", "edit_log") {
      LexiconFormatter.titleCase(
        it.logNoun
      )
    },
    // Position 1 is caller-supplied in each of these — a count, or the record's own title.
    "feature/logs/viewing:log_squawk_count_plural" to { l: Lexicon ->
      mapOf(2 to l.squawkNoun.plural)
    },
    "feature/logs/viewing:unknown_squawk" to { l: Lexicon -> mapOf(2 to l.squawkNoun.singular) },

    // The task half of the same association surfaces (#732). The squawk half was converted and
    // this was not, so a boat's log card counted "1 task" beside "1 issue" — one noun from the
    // template and one from the app. Each of these reads longer for aviation than it did, because
    // the airplane task noun is "maintenance task": the snapshot rows below record that, and the
    // log detail sheet has rendered "Affected Maintenance Tasks" from the same noun since #656.
    frame("feature/logs/viewing", "log_task_count_one") { it.taskNoun.singular },
    "feature/logs/viewing:log_task_count_plural" to { l: Lexicon ->
      mapOf(2 to l.taskNoun.plural)
    },
    "feature/logs/viewing:no_tasks_linked" to { l: Lexicon ->
      mapOf(1 to l.taskNoun.plural, 2 to l.logNoun.singular)
    },
    "feature/tasks/sharedassets:no_task_work_recorded" to { l: Lexicon ->
      mapOf(1 to l.taskNoun.plural, 2 to l.logNoun.singular)
    },
    "feature/tasks/sharedassets:unknown_task" to { l: Lexicon ->
      mapOf(2 to l.taskNoun.singular)
    },
    "feature/tasks/update:no_log_history" to { l: Lexicon ->
      mapOf(1 to l.logNoun.plural, 2 to l.taskNoun.singular)
    },
    "feature/tasks/viewing:no_maintenance_logs_for_task" to { l: Lexicon ->
      mapOf(1 to l.logNoun.plural, 2 to l.taskNoun.singular)
    },
    "feature/logs/sharedassets:resolve_squawk_work_description" to { l: Lexicon ->
      mapOf(2 to l.squawkNoun.singular)
    },
    "feature/logs/sharedassets:resolve_task_work_description" to { l: Lexicon ->
      mapOf(2 to l.taskNoun.singular)
    },

    // The remaining per-thing surfaces (#656). Absent on purpose: "Tail Number" is a *spec field*
    // label, not a lexicon noun (PRD §4.2, so #657); the collaboration channel description belongs
    // to #661 with the rest of the notification surface; and the stress-test copy is a developer
    // surface, excluded wholesale by the classification. The five overview_no_* strings are gone —
    // a rail's title is fixed text rather than a frame ("No work logs yet" is not what the aviation
    // app says), so they moved to `Lexicon.empty_states` with the rest of the empty-state copy.
    frame(
      "feature/thing/dashboard",
      "thing_load_error"
    ) { it.thingNoun.singular },
    frame(
      "feature/thing/dashboard",
      "overview_open_squawks"
    ) { it.squawkNoun.plural },
    frame("feature/thing/update", "delete_thing") {
      LexiconFormatter.titleCase(
        it.thingNoun
      )
    },
    frame("feature/thing/update", "update_thing") {
      LexiconFormatter.titleCase(
        it.thingNoun
      )
    },
    frame(
      "feature/squawk/update",
      "dismiss_squawk_warning"
    ) { it.squawkNoun.singular },
    // Positions 1 and 2 are the count and the member noun, both caller-supplied.
    "feature/thing/update:delete_thing_shared_warning" to { l: Lexicon ->
      mapOf(3 to l.thingNoun.singular)
    },

    // Strings that name a thing *in the abstract*, with none selected — "Add a ___", the empty
    // fleet, the subscription perks. These read from the app-scoped default in CurrentThingTemplate,
    // which is exactly the case it exists for and which retires itself: one preset means the only
    // right word, a second preset means the generic one. That is why they are conversions and not
    // hand-written neutral copy like the technician page (#684), whose list aggregates things that
    // already exist and may not share a template.
    frame(
      "core/sharedassets",
      "add_thing"
    ) { LexiconFormatter.titleCase(it.thingNoun) },
    frame(
      "core/sharedassets",
      "empty_add_thing"
    ) { LexiconFormatter.withArticle(it.thingNoun) },
    frame("feature/fleet/sharedassets", "add_first_thing") {
      LexiconFormatter.titleCase(it.thingNoun)
    },
    frame(
      "feature/fleet/sharedassets",
      "no_fleet_description"
    ) { it.thingNoun.singular },

    // Account-scoped surfaces that name a thing *in the abstract* (#687). Same case as the
    // add-thing family: they render with none selected, so CurrentThingTemplate's app-scoped
    // default answers them and retires itself when a second preset ships. Filing #687 lumped these
    // in with surfaces that aggregate *existing* things, which is a different problem.
    // Positions 1 and 2 are the tail number and the actor, both from the push payload.
    "feature/notifications/sharedassets:notification_n1_body_thing_updated" to { l: Lexicon ->
      mapOf(3 to l.thingNoun.singular)
    },
    // The urgency summaries. Positions 1 and 2 are the tail number and the count; the noun differs
    // by tier, which is why the scanner builds them per tier rather than from one resource handle.
    "feature/notifications/sharedassets:notification_body_due_soon_plural" to { l: Lexicon ->
      mapOf(3 to l.taskNoun.plural)
    },
    "feature/notifications/sharedassets:notification_body_overdue_plural" to { l: Lexicon ->
      mapOf(3 to l.taskNoun.plural)
    },
    "feature/notifications/sharedassets:notification_body_priority_raised_plural" to { l: Lexicon ->
      mapOf(3 to l.squawkNoun.plural)
    },
    // "New squawk", not "New Squawk" — mid-sentence, so the bare singular.
    frame(
      "feature/notifications/sharedassets",
      "notification_n1_title_squawk_created"
    ) {
      it.squawkNoun.singular
    },
    // Positions 1-3 are tail number, actor, and the record's own title.
    "feature/notifications/sharedassets:notification_n1_body_squawk_created" to { l: Lexicon ->
      mapOf(4 to l.squawkNoun.singular)
    },
    "feature/notifications/sharedassets:notification_n1_body_squawk_raised" to { l: Lexicon ->
      mapOf(4 to l.squawkNoun.singular)
    },
  )

  /** A single-argument frame at position 1, in any module. */
  private fun frame(
    module: String,
    resource: String,
    word: (Lexicon) -> String,
  ): Pair<String, (Lexicon) -> Map<Int, String>> =
    "$module:$resource" to { lexicon -> mapOf(1 to word(lexicon)) }

  /** A frame in `feature/sharing/sharedassets`. */
  private fun sharing(
    resource: String,
    args: (Lexicon) -> Map<Int, String>,
  ): Pair<String, (Lexicon) -> Map<Int, String>> =
    "feature/sharing/sharedassets:$resource" to args

  /** A single-argument frame in `feature/squawk/sharedassets` filled from the squawk noun. */
  private fun squawkFrame(
    resource: String,
    word: (Noun) -> String,
  ): Pair<String, (Lexicon) -> Map<Int, String>> =
    "feature/squawk/sharedassets:$resource" to { lexicon ->
      mapOf(
        1 to word(
          lexicon.squawkNoun
        )
      )
    }

  private data class Entry(
    val module: String,
    val resource: String,
    val value: String
  )

  @Test
  fun everyStringStillReadsExactlyAsItDidBeforePhase2() {
    val snapshot = loadSnapshot()
    val current = readAllStrings()
    val lexicon = AirplaneTemplate.AIRPLANE_LEXICON

    val missing = snapshot.keys - current.keys
    val added = current.keys - snapshot.keys
    val changed = mutableListOf<String>()
    val undeclared = mutableListOf<String>()

    for (key in snapshot.keys.intersect(current.keys)) {
      val was = snapshot.getValue(key)
      val recipe = LEXICON_ARGS[key]
      val now = current.getValue(key)
        .let { if (recipe == null) it else it.fill(recipe(lexicon)) }
      if (was == now) continue
      // A string that changed with no recipe was converted without declaring how it renders —
      // reported separately because the fix is different: declare it, don't revert it.
      if (recipe == null && current.getValue(key) != was) undeclared += key
      changed += "$key\n    was: $was\n    now: $now"
    }

    // Reported together rather than failing on the first, so one run shows the whole picture.
    assertThat(
      buildString {
        if (changed.isNotEmpty()) {
          appendLine("${changed.size} string(s) no longer render as they did:")
          changed.take(20)
            .forEach { appendLine("  $it") }
        }
        if (undeclared.isNotEmpty()) {
          appendLine(
            "${undeclared.size} of those have no LEXICON_ARGS entry. If they were converted, add " +
              "the recipe here in the same commit: ${undeclared.take(20)}",
          )
        }
        if (missing.isNotEmpty()) {
          appendLine("${missing.size} string(s) removed: ${missing.take(20)}")
        }
        if (added.isNotEmpty()) {
          // Additions are legitimate and common — a new feature adds strings. They are listed so
          // the snapshot is updated deliberately rather than drifting out of date silently.
          appendLine(
            "${added.size} new string(s) not in the snapshot: ${
              added.take(
                20
              )
            }"
          )
        }
      },
    ).isEmpty()
  }

  @Test
  fun theRenderPathReconstructsTheOriginalWording() {
    // While LEXICON_ARGS is empty this is the substitution's only coverage, and an unproven
    // substitution would leave every future conversion's evidence resting on untested machinery.
    // Wire generates message fields as nullable; the airplane lexicon populates all of them.
    val lexicon = AirplaneTemplate.AIRPLANE_LEXICON

    // Mid-sentence, so the bare singular — not sentenceCase, which would render "Add Aircraft".
    // Getting this wrong on the first example written is exactly the mistake the test is here for.
    assertThat("Add %1\$s".fill(mapOf(1 to lexicon.thing!!.singular)))
      .isEqualTo("Add aircraft")

    // A title, where the case does come from a formatter.
    assertThat("%1\$s".fill(mapOf(1 to LexiconFormatter.titleCase(lexicon.down_status_long))))
      .isEqualTo("Aircraft on Ground")

    // Caller-supplied positions must survive untouched: the snapshot recorded them as placeholders,
    // so the comparison only works if both sides still carry them literally.
    assertThat(
      "%1\$s: %2\$d %3\$s increased in priority"
        .fill(mapOf(3 to LexiconFormatter.plural(lexicon.squawk!!))),
    ).isEqualTo("%1\$s: %2\$d squawks increased in priority")

    // Two-digit positions must not be truncated by a prefix match on %1$s.
    assertThat("%1\$s %11\$s".fill(mapOf(11 to "x", 1 to "y"))).isEqualTo("y x")
  }

  @Test
  fun noConvertedStringIsReadWithoutItsArguments() {
    // The failure this exists for: adding "%1${'$'}s" to a string does not break compilation, so a
    // call site left as stringResource(Res.string.x) still builds and renders the placeholder
    // literally — "Leave this %1${'$'}s?" on screen. Nothing else catches it. The snapshot test
    // checks the resource's recipe, not how it is read, and there is no compiler error to trip on.
    val offenders = repoRoot().walkTopDown()
      .filter { it.extension == "kt" && "/build/" !in it.path }
      .flatMap { file ->
        val text = file.readText()
        LEXICON_ARGS.keys.map { it.substringAfter(":") }
          .filter { resource ->
            // A bare read: the resource name followed directly by ")" with no argument between.
            Regex("""stringResource\(\s*[A-Za-z]*Res\.string\.$resource\s*\)""").containsMatchIn(
              text
            ) ||
              Regex("""getString\(\s*[A-Za-z]*Res\.string\.$resource\s*\)""").containsMatchIn(
                text
              ) ||
              // Android's R.string too, not only Compose's Res.string. The OS notification channel
              // description is a res/values string read with getString(R.string.…), and a guard
              // that only knew about Compose resources waved it straight through (#662).
              Regex("""getString\(\s*R\.string\.$resource\s*\)""").containsMatchIn(
                text
              )
          }
          .map { "${file.name}: $it" }
      }
      .toList()

    assertThat(offenders).isEmpty()
  }

  @Test
  fun everyConvertedStringIsReadInline() {
    // The hole the bare-call check could not see, found in production rather than by a test:
    // ProUpsellSheet stored four StringResources in an enum and rendered them through one
    // `stringResource(trigger.bodyRes)`. Two of the four take the thing noun. Neither the compiler
    // nor the bare-call check nor the round-trip could tell — the reference is a *value*, so there
    // is no argument list at the reference to inspect — and "Share %1${'$'}s and invite others with
    // SquawkIt Pro." shipped with the placeholder showing.
    //
    // So a converted resource may only be named where it is read. Storing one in an enum, a map, a
    // val, or a parameter hides whether its arguments are ever supplied.
    val offenders = repoRoot().walkTopDown()
      .filter { it.extension == "kt" && "/build/" !in it.path && "androidHostTest" !in it.path }
      .flatMap { file ->
        val text = file.readText()
        Regex("""(?:[A-Za-z]*Res|R)\.string\.([a-z0-9_]+)""").findAll(text)
          .filter {
            it.groupValues[1] in LEXICON_ARGS.keys.map { k ->
              k.substringAfter(
                ":"
              )
            }
          }
          .filterNot { match ->
            val before = text.substring(0, match.range.first)
              .trimEnd()
            before.endsWith("stringResource(") || before.endsWith("getString(")
          }
          .map { "${file.name}: ${it.groupValues[1]}" }
      }
      .toList()

    assertThat(offenders).isEmpty()
  }

  @Test
  fun everyRecipeNamesAStringThatExists() {
    // A recipe whose resource was renamed or deleted stops doing anything, and its string silently
    // leaves coverage — the same failure as never declaring it.
    val current = readAllStrings()
    assertThat(LEXICON_ARGS.keys - current.keys).isEmpty()
  }

  @Test
  fun theSnapshotItselfIsIntact() {
    // A truncated or empty snapshot would make the test above pass vacuously — the failure mode
    // where a broken guard looks exactly like a satisfied one.
    //
    // A floor, not a census: the test above already reports additions and removals by name, so
    // this only has to catch a file that got cut off or clobbered. Lower it deliberately when
    // strings are legitimately deleted (66 dead ones went in #685; the nine empty-state strings
    // that moved into `Lexicon.empty_states` went here), never to get to green.
    val snapshot = loadSnapshot()
    assertThat(snapshot.size).isAtLeast(840)
    assertThat(snapshot).containsKey("app:app_name")
    assertThat(snapshot.getValue("app:app_name")).isEqualTo("SquawkIt")
  }

  /** Substitutes `%N${'$'}s` for the positions the lexicon supplies, leaving caller args alone. */
  private fun String.fill(args: Map<Int, String>): String =
    args.entries.fold(this) { acc, (position, value) ->
      acc.replace("%$position\$s", value)
    }

  private fun loadSnapshot(): Map<String, String> =
    javaClass.classLoader!!.getResourceAsStream("string_snapshot.tsv")!!
      .bufferedReader()
      .readLines()
      .asSequence()
      .filterNot { it.startsWith("#") || it.isBlank() || it.startsWith("module\t") }
      .map { it.split("\t", limit = 3) }
      .filter { it.size == 3 }
      .associate { (module, resource, value) -> "$module:$resource" to value.unescape() }

  /**
   * Reads `strings.xml` from source rather than through `Res.string`.
   *
   * Deliberate: the resource accessors need a composition or a suspending call, and this test's
   * subject is the authored text, not the runtime lookup. Reading source also means the test sees
   * a string the moment it is written, without a build step in between.
   */
  private fun readAllStrings(): Map<String, String> {
    val stringRe = Regex(
      """<string name="([^"]+)"[^>]*>(.*?)</string>""",
      RegexOption.DOT_MATCHES_ALL
    )
    return repoRoot().walkTopDown()
      .filter { it.name == "strings.xml" && "/build/" !in it.path }
      .flatMap { file ->
        val module = file.path.removePrefix(repoRoot().path + "/")
          .substringBefore("/src/")
        stringRe.findAll(file.readText())
          .map { m ->
            Entry(
              module,
              m.groupValues[1],
              m.groupValues[2].stripInlineMarkup()
            )
          }
      }
      .associate { "${it.module}:${it.resource}" to it.value }
  }

  /**
   * Drops inline markup, so the value compared is the one the app actually renders.
   *
   * `<xliff:g name="tail_number" example="N123AA">%1${'$'}s</xliff:g>` says what a placeholder is
   * for where a developer reads the string, instead of making them trace the call site. Compose
   * Multiplatform strips those tags when it compiles `strings.xml` into its `.cvr` format —
   * verified by decoding the output, which is byte-identical with and without them — so the app
   * sees plain `%1${'$'}s` either way.
   *
   * This test reads the XML directly rather than through the resource accessors, so without this it
   * would compare markup against a snapshot holding rendered text and fail on a string that had not
   * changed at all. Raw `<` only ever appears as a tag: a literal one is written `&lt;`.
   */
  private fun String.stripInlineMarkup(): String = replace(Regex("<[^>]+>"), "")

  /**
   * Restores real newlines and tabs from the sentinels the snapshot stores them as.
   *
   * The snapshot uses U+0001 and U+0002 rather than backslash escapes because `strings.xml` itself
   * contains literal `\n` — a backslash followed by an `n`, Android's own escape — as authored
   * text. A backslash scheme cannot tell that from an escaped real newline: unescaping `\\` and
   * `\n` in either order mangles one case or the other, and the first version of this file turned
   * all 12 of those strings into multi-line ones. Control characters cannot occur in a string
   * resource, so there is nothing for them to collide with.
   */
  private fun String.unescape(): String =
    replace('\u0001', '\n').replace('\u0002', '\t')

  /** Walks up from the module directory until `settings.gradle.kts` appears. */
  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = requireNotNull(dir.parentFile) {
        "settings.gradle.kts not found above ${System.getProperty("user.dir")}"
      }
    }
    return dir
  }
}
