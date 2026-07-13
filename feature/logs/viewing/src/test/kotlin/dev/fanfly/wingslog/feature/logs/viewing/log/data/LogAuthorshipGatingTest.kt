package dev.fanfly.wingslog.feature.logs.viewing.log.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.gitlive.firebase.auth.FirebaseAuth
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val AC_ID = "ac-1"
private const val OWNER_UID = "uid-owner"
private const val LOG_ID = "log-1"

/**
 * Attestation is a statement about other people. On an unshared aircraft nobody else can write a
 * log, so there is nothing to attest and nothing a reader could do about it — the owner typed every
 * name and answers for all of it. The sheet must say nothing at all there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogAuthorshipGatingTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private val logManager: MaintenanceLogManager = mockk(relaxed = true)
  private val tasks: TaskDataManager = mockk(relaxed = true)
  private val sharing: SharingManager = mockk(relaxed = true)
  private val technicians: TechnicianManager = mockk(relaxed = true)
  private val auth: FirebaseAuth = mockk(relaxed = true)

  // Hand-typed technician (no source_uid) — the case that now reports "not verified" when shared.
  private val log = MaintenanceLog(
    id = LOG_ID,
    work_description = "Oil change",
    technician = Technician(id = "m1", name = "Hand-typed Mechanic"),
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { logManager.observeLogs(AC_ID) } returns flowOf(listOf(log))
    every { logManager.observeLogAuthors(AC_ID) } returns flowOf(mapOf(LOG_ID to OWNER_UID))
    every { tasks.observeTasks(AC_ID) } returns flowOf(emptyList())
    every { sharing.observeLinkedTechnicians(AC_ID) } returns flowOf(emptyList())
    every { technicians.observeSelf() } returns flowOf(null)
    every { auth.currentUser } returns null
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = MaintenanceLogListViewModel(
    logManager = logManager,
    inspectionDataManager = tasks,
    sharingManager = sharing,
    technicianManager = technicians,
    auth = auth,
    aircraftId = AC_ID,
  )

  private fun selectedAuthorship(): LogAuthorship {
    val vm = viewModel()
    vm.onLogClick(log)
    return (vm.uiState.value as MaintenanceLogListUiState.Success).selectedAuthorship
  }

  @Test
  fun unsharedAircraft_saysNothing() {
    every { sharing.observeIsShared(AC_ID) } returns flowOf(false)

    assertThat(selectedAuthorship()).isEqualTo(LogAuthorship.Unknown)
  }

  @Test
  fun sharedAircraft_reportsTheHandTypedNameAsUnverifiable() {
    every { sharing.observeIsShared(AC_ID) } returns flowOf(true)

    assertThat(selectedAuthorship())
      .isEqualTo(LogAuthorship.Unverifiable("Hand-typed Mechanic"))
  }
}
