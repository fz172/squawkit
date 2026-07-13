package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateGroup
import dev.fanfly.wingslog.feature.technician.datamanager.merge.findDuplicates
import dev.fanfly.wingslog.feature.technician.datamanager.merge.signature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TechnicianListUiState(
  /** The user's own list: their self-record first, then mechanics they typed in by hand. */
  val technicians: List<Technician> = emptyList(),
  /**
   * Members of the user's shared aircraft who have published a technician mirror. Read-only — each
   * is maintained by the person it belongs to, not by this user (design §7.3).
   */
  val linkedTechnicians: List<Technician> = emptyList(),
  val selfId: String? = null,
  /** Rows that look like the same person, for the review sheet (design §7.4). */
  val duplicates: List<DuplicateGroup> = emptyList(),
  /** Identifies *these* duplicates; stored on dismiss so a NEW look-alike still prompts. */
  val duplicatesSignature: String = "",
  /** The signature the user last dismissed or merged, or null if they never have. */
  val reviewedSignature: String? = null,
  val showDuplicateReview: Boolean = false,
) {
  /**
   * Prompt when there is something to reconcile that the user has not already seen. Comparing
   * signatures rather than a boolean is what stops a single dismissal from muting every future
   * duplicate forever.
   */
  val showDuplicatePrompt: Boolean
    get() = duplicates.isNotEmpty() && duplicatesSignature != reviewedSignature
}

class TechnicianListViewModel(
  private val technicianManager: TechnicianManager,
  sharingManager: SharingManager,
) : ViewModel() {

  private val localState = MutableStateFlow(LocalState())

  val uiState: StateFlow<TechnicianListUiState> = combine(
    technicianManager.observeTechnicians(),
    technicianManager.observeSelfId(),
    sharingManager.observeLinkedTechnicians(),
    technicianManager.observeReviewedDuplicatesSignature(),
    localState,
  ) { technicians, selfId, linked, reviewedSignature, local ->
    val self = technicians.find { it.id == selfId }
    val others = technicians.filter { it.id != selfId }
      .sortedBy { it.name.lowercase() }

    // The self-record participates as a *keeper*, never as a duplicate: hand-typing yourself before
    // the app bootstrapped your profile is one of the commonest duplicates there is.
    val duplicates = findDuplicates(manual = others, mirrors = linked, self = self)

    TechnicianListUiState(
      technicians = listOfNotNull(self) + others,
      linkedTechnicians = linked,
      selfId = selfId,
      duplicates = duplicates,
      duplicatesSignature = duplicates.signature(),
      reviewedSignature = reviewedSignature,
      showDuplicateReview = local.showDuplicateReview,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(),
    initialValue = TechnicianListUiState(),
  )

  fun showDuplicateReview() = localState.update { it.copy(showDuplicateReview = true) }

  fun hideDuplicateReview() = localState.update { it.copy(showDuplicateReview = false) }

  /** "Not duplicates" — stop prompting about *these*, change nothing. A new one still prompts. */
  fun dismissDuplicatePrompt() {
    viewModelScope.launch {
      technicianManager.markDuplicatesReviewed(uiState.value.duplicatesSignature)
      localState.update { it.copy(showDuplicateReview = false) }
    }
  }

  /** Applies only the groups the user checked. Nothing is ever merged silently (§7.4). */
  fun applyMerges(groups: List<DuplicateGroup>) {
    viewModelScope.launch {
      technicianManager.applyDuplicateMerges(groups, uiState.value.duplicatesSignature)
      localState.update { it.copy(showDuplicateReview = false) }
    }
  }

  private data class LocalState(val showDuplicateReview: Boolean = false)
}
