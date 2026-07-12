package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val SELF_ID = "tech-self"
private const val LINKED_UID = "uid-linked-mechanic"

@OptIn(ExperimentalCoroutinesApi::class)
class TechnicianListViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var technicianManager: TechnicianManager
  private lateinit var sharingManager: SharingManager

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    technicianManager = mockk(relaxed = true)
    sharingManager = mockk(relaxed = true)

    every { technicianManager.observeTechnicians() } returns flowOf(emptyList())
    every { technicianManager.observeSelfId() } returns flowOf(null)
    every { sharingManager.observeLinkedTechnicians() } returns flowOf(emptyList())
    // A relaxed mock hands back an EMPTY flow, and an empty source in a combine means the state
    // never emits at all — stub it so the combine can produce.
    every { technicianManager.observeDuplicatesReviewed() } returns flowOf(true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun linkedTechnicians_areKeptSeparateFromTheOwnList() = runTest(testDispatcher) {
    every { technicianManager.observeTechnicians() } returns flowOf(
      listOf(
        Technician(id = SELF_ID, name = "Sponge Bob"),
        Technician(id = "manual-1", name = "Hand-typed Mechanic"),
      )
    )
    every { technicianManager.observeSelfId() } returns flowOf(SELF_ID)
    every { sharingManager.observeLinkedTechnicians() } returns flowOf(
      listOf(Technician(id = LINKED_UID, name = "Linked Mechanic", source_uid = LINKED_UID))
    )

    val state = viewModel().uiState.first { it.technicians.isNotEmpty() }

    // The personal list stays personal — a linked profile must never leak into it, or it would
    // look editable.
    assertThat(state.technicians.map { it.id })
      .containsExactly(SELF_ID, "manual-1")
      .inOrder()
    assertThat(state.linkedTechnicians.map { it.source_uid }).containsExactly(LINKED_UID)
  }

  @Test
  fun linkedTechnicians_carrySourceUid_soTheyReadAsFirstParty() = runTest(testDispatcher) {
    every { sharingManager.observeLinkedTechnicians() } returns flowOf(
      listOf(Technician(id = LINKED_UID, name = "Linked Mechanic", source_uid = LINKED_UID))
    )

    val state = viewModel().uiState.first { it.linkedTechnicians.isNotEmpty() }

    assertThat(state.linkedTechnicians.single().source_uid).isEqualTo(LINKED_UID)
  }

  @Test
  fun noShares_leavesLinkedSectionEmpty() = runTest(testDispatcher) {
    every { technicianManager.observeTechnicians() } returns flowOf(
      listOf(Technician(id = SELF_ID, name = "Sponge Bob"))
    )
    every { technicianManager.observeSelfId() } returns flowOf(SELF_ID)

    val state = viewModel().uiState.first { it.technicians.isNotEmpty() }

    assertThat(state.linkedTechnicians).isEmpty()
  }

  // ---- duplicate review (design §7.4) ----

  @Test
  fun aManualRowLookingLikeAMember_promptsForReview() = runTest(testDispatcher) {
    every { technicianManager.observeTechnicians() } returns flowOf(
      listOf(
        Technician(id = SELF_ID, name = "Me"),
        Technician(id = "m1", name = "Sponge Bob", cert_number = "AP-123"),
      )
    )
    every { technicianManager.observeSelfId() } returns flowOf(SELF_ID)
    every { sharingManager.observeLinkedTechnicians() } returns flowOf(
      listOf(
        Technician(
          id = LINKED_UID,
          name = "Sponge Bob",
          cert_number = "AP-123",
          source_uid = LINKED_UID,
        )
      )
    )
    every { technicianManager.observeDuplicatesReviewed() } returns flowOf(false)

    val state = viewModel().uiState.first { it.duplicates.isNotEmpty() }

    assertThat(state.showDuplicatePrompt).isTrue()
    assertThat(state.duplicates.single().keep.source_uid).isEqualTo(LINKED_UID)
  }

  @Test
  fun onceReviewed_theUserIsNotPromptedAgain() = runTest(testDispatcher) {
    every { technicianManager.observeTechnicians() } returns flowOf(
      listOf(Technician(id = "m1", name = "Sponge Bob", cert_number = "AP-123"))
    )
    every { sharingManager.observeLinkedTechnicians() } returns flowOf(
      listOf(
        Technician(
          id = LINKED_UID,
          name = "Sponge Bob",
          cert_number = "AP-123",
          source_uid = LINKED_UID,
        )
      )
    )
    every { technicianManager.observeDuplicatesReviewed() } returns flowOf(true)

    val state = viewModel().uiState.first { it.duplicates.isNotEmpty() }

    // The duplicates are still detected — the user just isn't nagged about them.
    assertThat(state.showDuplicatePrompt).isFalse()
  }

  @Test
  fun theSelfRecord_isNeverOfferedAsADuplicate() = runTest(testDispatcher) {
    // The self-record is the user. It can look like a member (same name, same certificate) without
    // being one, and merging it away would be destructive.
    every { technicianManager.observeTechnicians() } returns flowOf(
      listOf(Technician(id = SELF_ID, name = "Sponge Bob", cert_number = "AP-123"))
    )
    every { technicianManager.observeSelfId() } returns flowOf(SELF_ID)
    every { sharingManager.observeLinkedTechnicians() } returns flowOf(
      listOf(
        Technician(
          id = LINKED_UID,
          name = "Sponge Bob",
          cert_number = "AP-123",
          source_uid = LINKED_UID,
        )
      )
    )
    every { technicianManager.observeDuplicatesReviewed() } returns flowOf(false)

    val state = viewModel().uiState.first { it.technicians.isNotEmpty() }

    assertThat(state.duplicates).isEmpty()
    assertThat(state.showDuplicatePrompt).isFalse()
  }

  private fun viewModel() = TechnicianListViewModel(
    technicianManager = technicianManager,
    sharingManager = sharingManager,
  )
}
