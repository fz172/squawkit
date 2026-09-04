package dev.fanfly.wingslog.feature.shell.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.TemplateResolution
import dev.fanfly.wingslog.core.template.displayLabel
import dev.fanfly.wingslog.core.template.displaySubtitle
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.core.ui.adaptive.ShellThing
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.fleet.picker.data.SelectedThingStore
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.SyncNotice
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the ambient thing selection for the adaptive shell ([AdaptiveAppShell]).
 *
 * Per the redesign, the selected thing is app-level state chosen from the switcher rather than a
 * navigation argument carried per destination — see `docs/web/web_adaptive_layout_design.html` §6.
 */
class AdaptiveShellViewModel(
  private val fleetManager: FleetManager,
  private val technicianManager: TechnicianManager,
  private val authManager: AuthManager,
  private val sharingManager: SharingManager,
  private val subscriptionManager: SubscriptionManager,
  private val syncEngine: SyncEngine,
  private val selectedThingStore: SelectedThingStore,
  private val templateRegistry: TemplateRegistry,
  private val currentThingTemplate: CurrentThingTemplate,
) : ViewModel() {

  /**
   * Whether the account is at its owned-thing limit (Pro gate). Shared things are pointers into
   * another account's tree and never count against the limit. `false` while the capability is off
   * (default-open) since [SubscriptionManager.thingLimit] is unlimited then. The Add-thing
   * entry consults this to open the upsell instead of navigating; see subscription_design.html §4/§6.
   */
  val atThingLimit: StateFlow<Boolean> =
    combine(
      fleetManager.observeFleetDashboard(),
      subscriptionManager.thingLimit(),
    ) { fleet, limit ->
      limit != null && fleet.count { !it.shared } >= limit
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  /**
   * The thing remembered from the last session, used as the initial selection so the app reopens
   * on the same thing. Kept in sync with [SelectedThingStore] as the selection changes; falls
   * back to the first thing when the remembered one no longer exists.
   */
  private var rememberedThingId: String? = selectedThingStore.load()

  /**
   * A Thing just created, to be selected as soon as the fleet carries it. Held aside rather than
   * selected outright: the create form's write and the fleet flow's next emission race, and a
   * selection the collector cannot find in the fleet is what it treats as a deleted Thing.
   */
  private var pendingNewThingId: String? = null

  /**
   * Work the sync engine had to throw away (PRD D3). Surfaced from the shell because it outlives
   * whatever screen the user was on — the purge usually lands while they are somewhere else, or
   * while the app is backgrounded entirely.
   */
  val notice: StateFlow<SyncNotice?> = syncEngine.notices

  fun dismissNotice() = syncEngine.dismissNotice()

  private val _uiState = MutableStateFlow(AdaptiveShellUiState())
  val uiState: StateFlow<AdaptiveShellUiState> = _uiState.asStateFlow()

  init {
    observeSelf()
    // Republish on every app start: idempotent, and the only thing that heals a member doc whose
    // name predates the mirror (or a publish that failed offline). See design §7.2.
    viewModelScope.launch { sharingManager.publishTechnicianMirror() }
    viewModelScope.launch {
      fleetManager.observeFleetDashboard()
        .collect { fleet ->
          // Outside `update`: it may re-run its lambda under contention, and clearing the pending
          // id is a side effect that must happen exactly once.
          val arrived = pendingNewThingId?.takeIf { id -> fleet.any { it.thing.id == id } }
          if (arrived != null) pendingNewThingId = null
          _uiState.update { state ->
            val mapped = fleet.map { entry ->
              val ac = entry.thing
              val resolution = templateRegistry.resolve(ac)
              ShellThing(
                id = ac.id,
                label = ac.displayLabel(resolution.template),
                subtitle = ac.displaySubtitle(resolution.template),
                // resolve(), not ac.template: a Thing created before templates existed carries
                // none, and reading the field directly would render it in no words at all.
                template = resolution.template,
                renderable = resolution is TemplateResolution.Renderable,
              )
            }
            // A Thing just created wins; then the live selection, then the one remembered from
            // last session; fall back to the first thing when none still exists. Persist whatever
            // we land on so the memory tracks the effective selection (including the fallback
            // after the remembered one is gone).
            val selected = arrived
              ?: state.selectedThingId
                ?.takeIf { id -> mapped.any { it.id == id } }
              ?: rememberedThingId?.takeIf { id -> mapped.any { it.id == id } }
              ?: mapped.firstOrNull()?.id
            if (selected != rememberedThingId) {
              rememberedThingId = selected
              selectedThingStore.save(selected)
            }
            state.copy(things = mapped, selectedThingId = selected)
          }
          // After the update, not inside it: `update` may re-run its lambda under contention, so a
          // side effect there can fire more than once per change.
          publishLexicon()
        }
    }
  }

  /**
   * A record the shell should scroll to and highlight once its section renders, set by a tapped
   * notification for a single record. Not a navigation argument for the same reason the thing
   * selection isn't one (see this class's doc comment); the section body reads it as plain state and
   * calls [consumeScrollTarget] once it has been handed to the list.
   */
  private val _pendingScrollTargetId = MutableStateFlow<String?>(null)
  val pendingScrollTargetId: StateFlow<String?> =
    _pendingScrollTargetId.asStateFlow()

  fun consumeScrollTarget() {
    _pendingScrollTargetId.value = null
  }

  /**
   * Applies a tapped notification's target to shell state. Called from
   * [AdaptiveShellRoute][dev.fanfly.wingslog.feature.shell.AdaptiveShellRoute] rather than handled
   * through `HandleNotificationTaps` (`feature/shell/AppNavHelpers.kt`), because none of it is a
   * navigation argument — thing selection, section, and the scroll target are all app-level
   * ViewModel state (design §5.3, and this class's own doc comment above).
   *
   * Every variant lands the pilot *in the list*, on the record, rather than in its edit form: a
   * notification says something changed, so the useful destination is the record in the context of
   * everything around it. Opening the editor would presume they want to change it, and would put an
   * unsaved-changes guard between them and a glance.
   *
   * Subscribing to `NotificationTapRouter.pending` and calling `consume()` belongs to that call
   * site, not here: the router is a process-wide singleton whose targets are consume-once, so only
   * a composable — which knows when the shell is actually on screen — can decide when a target has
   * really been acted on.
   */
  fun onNotificationTap(target: NotificationTapTarget) {
    selectThing(target.thingId)
    when (target) {
      // A summary notification: the tier picks the list, and there is no one record to point at.
      is NotificationTapTarget.Thing -> {
        _pendingScrollTargetId.value = null
        target.tab?.toShellSection()
          ?.let { selectSection(it) }
      }

      is NotificationTapTarget.Squawk -> {
        selectSection(ShellSection.SQUAWKS)
        _pendingScrollTargetId.value = target.squawkId
      }

      is NotificationTapTarget.Task -> {
        selectSection(ShellSection.TASKS)
        _pendingScrollTargetId.value = target.taskId
      }

      is NotificationTapTarget.Log -> {
        selectSection(ShellSection.LOGS)
        _pendingScrollTargetId.value = target.logId
      }
    }
  }

  /**
   * The four tabs the server can name, which are exactly `thingTabForRecordType`'s four returns
   * plus nothing else — `overview` arrives both for thing-level activity and for the §7.4
   * high-volume notice. Keep this in step with that function: an unmapped tab is not an error, it
   * just leaves the pilot on whatever section was already open with no clue what changed, which
   * reads as a tap that did nothing.
   */
  /**
   * Publishes the selected thing's words app-scoped.
   *
   * The per-thing form dialogs are registered on the root nav graph and compose outside this shell,
   * so they cannot read a CompositionLocal the shell installs — see [CurrentThingTemplate].
   */
  private fun publishLexicon() {
    val selected = _uiState.value.selectedThing?.template
    if (selected == null) currentThingTemplate.clear() else currentThingTemplate.set(
      selected
    )
  }

  private fun String.toShellSection(): ShellSection? = when (this) {
    "squawks" -> ShellSection.SQUAWKS
    "tasks" -> ShellSection.TASKS
    "logs" -> ShellSection.LOGS
    "overview" -> ShellSection.DASHBOARD
    else -> null
  }

  /** Current user's name + photo for the sidebar account/settings entry. */
  private fun observeSelf() {
    viewModelScope.launch {
      technicianManager.observeSelf()
        .collect { self ->
          val user = authManager.getCurrentUser()
          _uiState.update {
            it.copy(
              accountPhotoUrl = user?.photoURL,
              accountName = self?.name?.takeIf { name -> name.isNotBlank() }
                ?: user?.displayName?.takeIf { name -> name.isNotBlank() }
                ?: user?.email?.takeIf { email -> email.isNotBlank() },
            )
          }
        }
    }
  }

  /**
   * A Thing the create form just wrote. Selected now if the fleet already carries it, otherwise
   * the moment it does — see [pendingNewThingId].
   */
  fun selectNewThing(id: String) {
    if (uiState.value.things.any { it.id == id }) {
      selectThing(id)
    } else {
      pendingNewThingId = id
    }
  }

  /** Switcher selection (above phone): swaps the ambient thing in place and remembers it. */
  fun selectThing(id: String) {
    _uiState.update { it.copy(selectedThingId = id) }
    publishLexicon()
    rememberedThingId = id
    selectedThingStore.save(id)
  }

  /** Switches the active top-level section. */
  fun selectSection(section: ShellSection) {
    _uiState.update { it.copy(section = section) }
  }

  /** Open the global Settings section in the shell. */
  fun openSettings() {
    _uiState.update { it.copy(section = ShellSection.SETTINGS) }
  }
}
