package dev.fanfly.wingslog.core.ui.adaptive.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.layout.layoutTierFor
import dev.fanfly.wingslog.core.ui.perf.SwitchTrace
import dev.fanfly.wingslog.core.ui.perf.TraceFrames

/**
 * The adaptive web/tablet shell.
 *
 * Navigation container by tier:
 * - **MEDIUM / EXPANDED / LARGE** — the custom [WingsSidebar] (brand, the selected thing, its
 *   sections, the switch list, an account footer); MEDIUM draws it narrower with abbreviated
 *   labels. There is no icon rail.
 * - **COMPACT** — a floating pill bottom bar with the switcher in the top bar.
 *
 * Section bodies are supplied by the host via [sectionContent] (M3: real per-thing content), and
 * the no-thing prompt by [emptyFleetContent] — both are host slots because real content lives in
 * feature modules that `core:ui` cannot depend on. Tier is derived from the measured
 * [BoxWithConstraints] width (not `LocalWindowInfo`, which is unreliable on Kotlin/JS).
 */
@Composable
fun AdaptiveAppShell(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectThing: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onAddThing: () -> Unit,
  // #209: opens the manual invite-code entry surface. Null when thing sharing is gated off for
  // the build, which removes the switcher affordance entirely.
  onEnterInviteCode: (() -> Unit)? = null,
  sectionContent: @Composable (section: ShellSection, thingId: String?) -> Unit,
  emptyFleetContent: @Composable () -> Unit,
  // Per-section floating action button (Add log / task / squawk). A host slot because the add
  // actions navigate into feature screens that `core:ui` cannot depend on. Rendered in the shell's
  // own Scaffold slot so snackbars offset around it automatically.
  sectionFab: @Composable (section: ShellSection, thingId: String?) -> Unit = { _, _ -> },
  // Shared across every tier so a caller can drive snackbars (e.g. a cross-screen success message)
  // from a single instance regardless of which shell layout is currently active.
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val tracedSelectSection: (ShellSection) -> Unit = { section ->
    SwitchTrace.begin("${state.section} -> $section")
    onSelectSection(section)
  }
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val tier = layoutTierFor(maxWidth)
    // Per-thing sections render in the selected thing's words. SETTINGS does not: it is a global
    // surface and should read the same whatever the picker has selected, so it stays on the generic
    // lexicon (template_system_design.md §9).
    //
    // **This constrains which settings strings may be converted.** A settings string is only a
    // candidate if its *generic* rendering is acceptable, because that is the only rendering it
    // will ever get. "Whole fleet in one logbook" must therefore stay fixed text — converted, it
    // would read "Whole stuff in one logbook" for everyone. Where the generic and template words
    // coincide the conversion is free: the technician noun is "technician" in both, which is why
    // the settings row for it converts without changing.
    //
    // Detail screens reached *from* settings are root destinations, not section bodies, so they
    // keep the thing lexicon and can be domain-specific — see TechnicianListScreen.
    //
    // The FAB is wrapped too, since it says "New squawk".
    val thingLexicon = LocalThingLexicon.current
    fun lexiconFor(section: ShellSection) =
      if (section == ShellSection.SETTINGS) GenericLexicon.LEXICON
      // LocalThingLexicon, not the Thing's stored copy: the words are resolved once from this
      // build by CurrentThingTemplate and provided above both NavHosts. Reading the DNA here would
      // reintroduce the frozen-at-creation lexicon on exactly the per-thing surfaces that matter.
      else thingLexicon
    val content: @Composable () -> Unit = {
      SwitchTrace.step("shell composing ${state.section}")
      TraceFrames(state.section)
      CompositionLocalProvider(LocalThingLexicon provides lexiconFor(state.section)) {
        sectionContent(state.section, state.selectedThingId)
      }
    }
    val fab: @Composable () -> Unit = {
      CompositionLocalProvider(LocalThingLexicon provides lexiconFor(state.section)) {
        sectionFab(state.section, state.selectedThingId)
      }
    }
    CompositionLocalProvider(LocalLayoutTier provides tier) {
      when {
        state.things.isEmpty() ->
          EmptyFleetShell(
            tier = tier,
            state = state,
            onSelectSection = tracedSelectSection,
            onOpenSettings = onOpenSettings,
            settingsContent = { sectionContent(ShellSection.SETTINGS, null) },
            emptyFleetContent = emptyFleetContent,
            snackbarHostState = snackbarHostState,
          )

        tier.hasFullSidebar ->
          SidebarShell(
            state = state,
            onSelectSection = tracedSelectSection,
            onSelectThing = onSelectThing,
            onOpenSettings = onOpenSettings,
            onAddThing = onAddThing,
            onEnterInviteCode = onEnterInviteCode,
            content = content,
            fab = fab,
            snackbarHostState = snackbarHostState,
          )

        else ->
          ScaffoldShell(
            state = state,
            onSelectSection = tracedSelectSection,
            onSelectThing = onSelectThing,
            onOpenSettings = onOpenSettings,
            onAddThing = onAddThing,
            onEnterInviteCode = onEnterInviteCode,
            content = content,
            fab = fab,
            snackbarHostState = snackbarHostState,
          )
      }
    }
  }
}
