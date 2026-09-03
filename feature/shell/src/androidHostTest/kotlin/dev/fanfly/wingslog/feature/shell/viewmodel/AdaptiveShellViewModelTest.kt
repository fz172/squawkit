package dev.fanfly.wingslog.feature.shell.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.fleet.picker.data.SelectedThingStore
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateGroup
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveShellViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val fleet = MutableStateFlow<List<FleetEntry>>(emptyList())
  private val self = MutableStateFlow<Technician?>(null)
  private val fleetManager = object : FleetManager {
    override fun observeFleetDashboard(): Flow<List<FleetEntry>> = fleet
    override suspend fun updateThing(thing: Thing) =
      Result.success(true)

    override fun loadThing(id: String): Flow<Thing?> =
      MutableStateFlow(null)

    override suspend fun deleteThing(id: String) = Result.success(true)
  }
  private val technicianManager = object : TechnicianManager {
    override fun observeTechnicians(): Flow<List<Technician>> =
      MutableStateFlow(emptyList())

    override fun loadTechnician(id: String): Flow<Technician?> =
      MutableStateFlow(null)

    override fun observeSelf(): Flow<Technician?> = self
    override fun observeSelfId(): Flow<String?> = MutableStateFlow(null)
    override suspend fun updateTechnician(technician: Technician) =
      Result.success(true)

    override suspend fun deleteTechnician(id: String) = Result.success(true)
    override suspend fun saveSelfName(name: String) = Result.success(Unit)
    override suspend fun ensureSelfProfile() =
      Result.success(Unit)

    override suspend fun applyDuplicateMerges(
      groups: List<DuplicateGroup>,
      reviewedSignature: String,
    ) = Result.success(Unit)

    override fun observeReviewedDuplicatesSignature(): Flow<String?> =
      MutableStateFlow(null)

    override suspend fun markDuplicatesReviewed(signature: String) =
      Result.success(Unit)
  }
  private val authManager = object : AuthManager {
    override fun getCurrentUser(): FirebaseUser? = null
    override suspend fun trySilentLogin(): FirebaseUser? = null
    override suspend fun signInWithGoogle(): FirebaseUser? = null
    override suspend fun signInWithApple(): FirebaseUser? = null
    override suspend fun signInAnonymously(): FirebaseUser? = null
    override suspend fun updateDisplayName(name: String) = Unit
    override suspend fun logOut() = Unit
    override suspend fun sendSignInLink(email: String): SendLinkResult =
      SendLinkResult.Failed("not used")

    override fun isSignInWithEmailLink(link: String): Boolean = false
    override suspend fun completeSignInLink(
      email: String,
      link: String
    ): FirebaseUser? = null

    override suspend fun upgradeAnonymousAccount(
      provider: AuthProvider,
    ): AccountUpgradeResult = AccountUpgradeResult.Cancelled

    override suspend fun completeUpgradeWithEmailLink(
      email: String,
      link: String,
    ): AccountUpgradeResult = AccountUpgradeResult.Cancelled

    override suspend fun mergeIntoExistingAccount(
      provider: AuthProvider,
    ): AccountUpgradeResult = AccountUpgradeResult.Cancelled

    override suspend fun signInToExistingAccount(credential: AuthCredential): AccountUpgradeResult =
      AccountUpgradeResult.Cancelled
  }

  @Before
  fun setUp() = Dispatchers.setMain(testDispatcher)

  @After
  fun tearDown() = Dispatchers.resetMain()

  @Test
  fun publishesTheSelectedThingsLexiconAppScoped() = runTest(testDispatcher) {
    // Guards a bug class the byte-identical snapshot test cannot see (#658, #656). The per-thing
    // form dialogs are root nav destinations composed in DialogHost, a sibling of the shell, so
    // they cannot read a CompositionLocal the shell installs. If this stops being published they
    // fall back to the generic lexicon and render "New issue" where the app says "New squawk" —
    // a visible regression with a green test suite.
    fleet.value = listOf(thing("a1", "N1"))
    viewModel()

    assertThat(currentThingTemplate.lexicon.value.squawkNoun.singular).isEqualTo(
      "squawk"
    )
  }

  @Test
  fun anEmptyFleetSpeaksTheGenericWords() = runTest(testDispatcher) {
    // A technician with no thing of their own lands on the redeem / invite-code flow as their first
    // screen, with nothing selected. This used to say "aircraft": while airplane was the only
    // preset it was the only right answer. Six more presets ship now (#721-#723), so there is no
    // single right answer and the generic lexicon is what an account holding a house and an
    // airplane can honestly be shown — the retirement CurrentThingTemplate.default always
    // described.
    fleet.value = emptyList()
    viewModel()

    assertThat(currentThingTemplate.lexicon.value.thingNoun.singular).isEqualTo(
      "thing"
    )
  }

  @Test
  fun selectingAThingStillSpeaksItsOwnWords() = runTest(testDispatcher) {
    // The other half, and the one that matters more: a fleet with an airplane in it reads
    // "aircraft" exactly as before. The generic fallback applies to no-selection surfaces only.
    fleet.value = listOf(thing("a1", "N1"))
    viewModel()

    assertThat(currentThingTemplate.lexicon.value.thingNoun.singular).isEqualTo(
      "aircraft"
    )
  }

  @Test
  fun eachRowCarriesItsOwnCollectionLabel() = runTest(testDispatcher) {
    // The switcher groups by this. Read off the Thing's frozen DNA it came back empty for
    // anything created before collection_label existed, so every group heading said "Stuff" —
    // resolving through the registry is what makes them Fleet / Garage / Property.
    fleet.value = listOf(
      thing("a1", "N1"),
      home("h1"),
    )
    val rows = viewModel().uiState.value.things

    assertThat(rows.map { it.collectionLabel })
      .containsExactly("Fleet", "Property")
  }

  @Test
  fun aNamelessHomeStillHasARowLabel() = runTest(testDispatcher) {
    // The home preset declares no title_candidate, no is_identifier, and a house has no make or
    // model — so a nameless one exhausted every branch and rendered as an icon, a checkmark and a
    // blank line. Whatever the template *does* ask for stands in.
    fleet.value = listOf(home("h1"))
    val row = viewModel().uiState.value.things.single()

    assertThat(row.label).isEqualTo("655 Disko Drive")
  }

  @Test
  fun aHomeWithNothingFilledInFallsBackToItsType() = runTest(testDispatcher) {
    // The floor: no name and no spec values at all still names the row rather than leaving a gap.
    fleet.value = listOf(home("h1", address = ""))
    val row = viewModel().uiState.value.things.single()

    assertThat(row.label).isEqualTo("Home")
  }

  /** A home: no make, no model, no tail number — the preset that finds aviation assumptions. */
  private fun home(id: String, address: String = "655 Disko Drive") =
    FleetEntry(
      thing = Thing(
        id = id,
        spec = if (address.isEmpty()) emptyList()
        else listOf(Spec(key = "address", value_ = address)),
        template = CanonicalTemplates.HOME,
      ),
      shared = false,
      role = ShareRole.SHARE_ROLE_OWNER,
    )

  private fun thing(
    id: String,
    tail: String,
    make: String = "Cessna",
    model: String = "172",
    shared: Boolean = false,
    template: ThingTemplate? = null,
  ) =
    FleetEntry(
      // Spec entries, not fields 2-6 — those are reserved (#668), and the shell reads the same
      // keys the rest of the app does.
      thing = Thing(
        id = id,
        spec = listOf(
          Spec(key = SpecKeys.MAKE, value_ = make),
          Spec(key = SpecKeys.MODEL, value_ = model),
          Spec(key = SpecKeys.TAIL_NUMBER, value_ = tail),
        ),
        template = template,
      ),
      shared = shared,
      role = ShareRole.SHARE_ROLE_OWNER,
    )

  /** DNA from a build newer than this one — the §6.2 case. */
  private fun fromANewerBuild(): ThingTemplate =
    AirplaneTemplate.TEMPLATE.copy(min_app_version = THIS_BUILD + 1)

  // The shell republishes the technician mirror on app start (design §7.2); irrelevant to these
  // assertions, so a relaxed mock keeps it out of the way.
  private val sharingManager: SharingManager = mockk(relaxed = true)

  // The engine only feeds the discarded-changes notice here; irrelevant to these assertions.
  private val syncEngine: SyncEngine = mockk(relaxed = true)

  // Drives the owned-thing gate; null = unlimited (default-open, capability off).
  private val thingLimit = MutableStateFlow<Int?>(null)
  private val subscriptionManager: SubscriptionManager = mockk {
    every { thingLimit() } returns thingLimit
  }

  // In-memory device-local selection store; starts empty so tests behave like a fresh install
  // unless they seed [selectedThingStore.saved].
  private val selectedThingStore = object : SelectedThingStore {
    var saved: String? = null
    override fun load(): String? = saved
    override fun save(thingId: String?) {
      saved = thingId
    }
  }

  // The real registry, not a mock: it is pure data with no I/O, and a mock here would let a
  // ShellThing be built with a lexicon no template would ever produce.
  private val templateRegistry =
    BakedInTemplateRegistry(appVersionCode = THIS_BUILD)

  private val currentThingTemplate = CurrentThingTemplate(templateRegistry)

  private fun viewModel() = AdaptiveShellViewModel(
    fleetManager = fleetManager,
    technicianManager = technicianManager,
    authManager = authManager,
    sharingManager = sharingManager,
    subscriptionManager = subscriptionManager,
    syncEngine = syncEngine,
    selectedThingStore = selectedThingStore,
    templateRegistry = templateRegistry,
    currentThingTemplate = currentThingTemplate,
  )

  @Test
  fun mapsFleetAndSelectsFirstByDefault() = runTest(testDispatcher) {
    fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
    val vm = viewModel()

    val s = vm.uiState.value
    // An airplane with no name of its own falls back to its identifier — the tail number — so the
    // switcher reads exactly as it did before the label became template-driven.
    assertThat(s.things.map { it.label }).containsExactly("N1", "N2")
      .inOrder()
    assertThat(s.things.first().subtitle).isEqualTo("Cessna 172")
    assertThat(s.selectedThingId).isEqualTo("a1")
    assertThat(s.section).isEqualTo(ShellSection.DASHBOARD)
  }

  @Test
  fun aThingWithNoAviationSpecKeysStillHasASwitcherLabel() {
    // The bug home found: both switcher lines came from tail number and make/model, which a home
    // declares none of — so its row rendered as a blank gap with a checkmark beside it.
    val home = FleetEntry(
      thing = Thing(
        id = "h1",
        name = "1421 Maple Street",
        spec = listOf(Spec(key = "address", value_ = "1421 Maple Street")),
        template = CanonicalTemplates.HOME,
      ),
      shared = false,
      role = ShareRole.SHARE_ROLE_OWNER,
    )
    fleet.value = listOf(home)

    val row = viewModel().uiState.value.things.single()

    assertThat(row.label).isEqualTo("1421 Maple Street")
    // Nothing to add: no make, no model, and the name already is the only thing it has.
    assertThat(row.subtitle).isEmpty()
  }

  @Test
  fun keepsSelectionAcrossReemissionWhenStillPresent() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
      val vm = viewModel()
      vm.selectThing("a2")

      // Re-emit with the same thing; the explicit selection must survive.
      fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
      assertThat(vm.uiState.value.selectedThingId).isEqualTo("a2")

      // Remove the selected one; selection falls back to the first remaining.
      fleet.value = listOf(thing("a1", "N1"))
      assertThat(vm.uiState.value.selectedThingId).isEqualTo("a1")
    }

  @Test
  fun selectsFirstThingWhenFleetArrivesAfterEmptyState() =
    runTest(testDispatcher) {
      fleet.value = emptyList()
      val vm = viewModel()

      assertThat(vm.uiState.value.selectedThingId).isNull()

      fleet.value = listOf(thing("a1", "N1"))

      val s = vm.uiState.value
      assertThat(s.selectedThingId).isEqualTo("a1")
      assertThat(s.section).isEqualTo(ShellSection.DASHBOARD)
    }

  @Test
  fun restoresRememberedThingFromStore() = runTest(testDispatcher) {
    // Simulate a previous session that left "a2" selected on this device.
    selectedThingStore.saved = "a2"
    fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
    val vm = viewModel()

    assertThat(vm.uiState.value.selectedThingId).isEqualTo("a2")
  }

  @Test
  fun fallsBackToFirstWhenRememberedThingIsGone() = runTest(testDispatcher) {
    // The remembered thing was deleted since last session.
    selectedThingStore.saved = "deleted"
    fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
    val vm = viewModel()

    assertThat(vm.uiState.value.selectedThingId).isEqualTo("a1")
    // The effective selection is written back so the stale id doesn't linger.
    assertThat(selectedThingStore.saved).isEqualTo("a1")
  }

  @Test
  fun selectThingPersistsChoice() = runTest(testDispatcher) {
    fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
    val vm = viewModel()

    vm.selectThing("a2")

    assertThat(selectedThingStore.saved).isEqualTo("a2")
  }

  @Test
  fun selectSectionUpdatesSection() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.selectSection(ShellSection.SQUAWKS)
    assertThat(vm.uiState.value.section).isEqualTo(ShellSection.SQUAWKS)
  }

  @Test
  fun openSettingsSelectsSettingsAndEnters() = runTest(testDispatcher) {
    val vm = viewModel()

    vm.openSettings()
    val s = vm.uiState.value
    assertThat(s.section).isEqualTo(ShellSection.SETTINGS)
  }

  @Test
  fun notAtLimitWhenThingLimitIsUnlimited() = runTest(testDispatcher) {
    thingLimit.value = null
    fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
    val vm = viewModel()

    assertThat(vm.atThingLimit.value).isFalse()
  }

  @Test
  fun atLimitWhenOwnedCountReachesLimit() = runTest(testDispatcher) {
    thingLimit.value = 1
    fleet.value = listOf(thing("a1", "N1"))
    val vm = viewModel()

    assertThat(vm.atThingLimit.value).isTrue()
  }

  @Test
  fun sharedThingDoNotCountAgainstTheLimit() = runTest(testDispatcher) {
    thingLimit.value = 2
    // One owned + one shared against a limit of 2: only the owned one counts, so still under limit.
    // If the shared pointer counted, 2 >= 2 would trip the gate.
    fleet.value = listOf(
      thing("a1", "N1", shared = false),
      thing("a2", "N2", shared = true)
    )
    val vm = viewModel()

    assertThat(vm.atThingLimit.value).isFalse()
  }

  @Test
  fun observesSelfProfileForSidebarAccountEntry() = runTest(testDispatcher) {
    val vm = viewModel()

    self.value = Technician(id = "self", name = "Avery Park")

    assertThat(vm.uiState.value.accountName).isEqualTo("Avery Park")
  }

  // Notification taps (design §5.3). Every variant selects the thing and lands in a section; the
  // record variants additionally publish a scroll target instead of opening the record's edit form.

  @Test
  fun notificationTap_squawk_selectsThingSectionAndScrollTarget() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"), thing("a2", "N2"))
      val vm = viewModel()

      vm.onNotificationTap(
        NotificationTapTarget.Squawk(
          thingId = "a2",
          squawkId = "sq-1"
        )
      )

      assertThat(vm.uiState.value.selectedThingId).isEqualTo("a2")
      assertThat(vm.uiState.value.section).isEqualTo(ShellSection.SQUAWKS)
      assertThat(vm.pendingScrollTargetId.value).isEqualTo("sq-1")
    }

  @Test
  fun notificationTap_task_selectsThingSectionAndScrollTarget() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"))
      val vm = viewModel()

      vm.onNotificationTap(
        NotificationTapTarget.Task(
          thingId = "a1",
          taskId = "task-1"
        )
      )

      assertThat(vm.uiState.value.section).isEqualTo(ShellSection.TASKS)
      assertThat(vm.pendingScrollTargetId.value).isEqualTo("task-1")
    }

  @Test
  fun notificationTap_log_selectsThingSectionAndScrollTarget() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"))
      val vm = viewModel()

      vm.onNotificationTap(
        NotificationTapTarget.Log(
          thingId = "a1",
          logId = "log-1"
        )
      )

      assertThat(vm.uiState.value.section).isEqualTo(ShellSection.LOGS)
      assertThat(vm.pendingScrollTargetId.value).isEqualTo("log-1")
    }

  @Test
  fun notificationTap_thingSummary_selectsTabWithoutAScrollTarget() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"))
      val vm = viewModel()

      vm.onNotificationTap(
        NotificationTapTarget.Thing(
          thingId = "a1",
          tab = "tasks"
        )
      )

      assertThat(vm.uiState.value.section).isEqualTo(ShellSection.TASKS)
      // A summary covers several records, so there is no single card to scroll to.
      assertThat(vm.pendingScrollTargetId.value).isNull()
    }

  /**
   * The server names four tabs (`thingTabForRecordType`), not two. `logs` reaches a pilot whenever
   * a collaborator adds a logbook entry, and `overview` carries both thing-level activity and the
   * §7.4 high-volume notice — an unmapped tab silently leaves the pilot wherever they already were.
   */
  @Test
  fun notificationTap_thingSummary_selectsEveryTabTheServerCanSend() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"))

      for ((tab, expected) in listOf(
        "squawks" to ShellSection.SQUAWKS,
        "tasks" to ShellSection.TASKS,
        "logs" to ShellSection.LOGS,
        "overview" to ShellSection.DASHBOARD,
      )) {
        val vm = viewModel()
        vm.onNotificationTap(
          NotificationTapTarget.Thing(
            thingId = "a1",
            tab = tab
          )
        )
        assertThat(vm.uiState.value.section).isEqualTo(expected)
      }
    }

  @Test
  fun notificationTap_thingSummary_clearsAScrollTargetLeftByAnEarlierTap() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"))
      val vm = viewModel()
      vm.onNotificationTap(
        NotificationTapTarget.Task(
          thingId = "a1",
          taskId = "task-1"
        )
      )

      vm.onNotificationTap(
        NotificationTapTarget.Thing(
          thingId = "a1",
          tab = "tasks"
        )
      )

      // Otherwise the summary tap would re-highlight whichever record the previous tap pointed at.
      assertThat(vm.pendingScrollTargetId.value).isNull()
    }

  @Test
  fun notificationTap_thingSummaryWithoutTab_keepsCurrentSection() =
    runTest(testDispatcher) {
      fleet.value = listOf(thing("a1", "N1"))
      val vm = viewModel()
      vm.selectSection(ShellSection.LOGS)

      vm.onNotificationTap(
        NotificationTapTarget.Thing(
          thingId = "a1",
          tab = null
        )
      )

      assertThat(vm.uiState.value.section).isEqualTo(ShellSection.LOGS)
    }

  @Test
  fun consumeScrollTarget_clearsIt() = runTest(testDispatcher) {
    fleet.value = listOf(thing("a1", "N1"))
    val vm = viewModel()
    vm.onNotificationTap(
      NotificationTapTarget.Task(
        thingId = "a1",
        taskId = "task-1"
      )
    )

    vm.consumeScrollTarget()

    // The section body consumes once it has handed the id to the list, so returning to that section
    // later does not re-run the jump.
    assertThat(vm.pendingScrollTargetId.value).isNull()
  }

  // --- A Thing this build cannot render is degraded, never hidden (#728) ---

  @Test
  fun aThingWithDnaFromANewerBuildStaysInTheSwitcher() {
    // The rule that makes this state safe: it is the user's data, so it is listed either way. Only
    // `renderable` differs, and that is what stops the sections drawing it under wrong labels.
    fleet.value = listOf(
      thing("a1", "N1"),
      thing("a2", "N2", template = fromANewerBuild()),
    )

    val s = viewModel().uiState.value

    assertThat(s.things.map { it.id }).containsExactly("a1", "a2")
      .inOrder()
    assertThat(s.things.map { it.renderable }).containsExactly(true, false)
      .inOrder()
  }

  @Test
  fun aDegradedThingStillCountsAgainstTheOwnedLimit() =
    runTest(testDispatcher) {
      // Hiding it from the count would hand out a free slot for data the account really holds.
      thingLimit.value = 1
      fleet.value = listOf(thing("a1", "N1", template = fromANewerBuild()))

      assertThat(viewModel().atThingLimit.value).isTrue()
    }

  @Test
  fun anAirplaneIsNamedByItsTailNumberRatherThanItsSerial() {
    // The airplane declares two identifiers, and picking "the first" showed the serial the moment
    // the spec fields were reordered. The template says which one names the thing (PRD §4.2).
    fleet.value = listOf(
      FleetEntry(
        thing = Thing(
          id = "a1",
          spec = listOf(
            Spec(key = SpecKeys.MAKE, value_ = "Cessna"),
            Spec(key = SpecKeys.MODEL, value_ = "172"),
            Spec(key = SpecKeys.SERIAL, value_ = "SN-99999"),
            Spec(key = SpecKeys.TAIL_NUMBER, value_ = "N123AB"),
          ),
          template = AirplaneTemplate.TEMPLATE,
        ),
        shared = false,
        role = ShareRole.SHARE_ROLE_OWNER,
      ),
    )

    val row = viewModel().uiState.value.things.single()

    assertThat(row.label).isEqualTo("N123AB")
    assertThat(row.subtitle).isEqualTo("Cessna 172")
  }
}

/** Any fixed value; the tests build DNA relative to it rather than to the real versionCode. */
private const val THIS_BUILD = 1000
