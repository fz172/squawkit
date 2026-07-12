package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TechnicianListUiState(
  /** The user's own list: their self-record first, then mechanics they typed in by hand. */
  val technicians: List<Technician> = emptyList(),
  /**
   * Members of the user's shared aircraft who have published a technician mirror. Read-only — each
   * is maintained by the person it belongs to, not by this user (design §7.3).
   */
  val linkedTechnicians: List<Technician> = emptyList(),
  val selfId: String? = null,
)

class TechnicianListViewModel(
  technicianManager: TechnicianManager,
  sharingManager: SharingManager,
) : ViewModel() {

  val uiState: StateFlow<TechnicianListUiState> = combine(
    technicianManager.observeTechnicians(),
    technicianManager.observeSelfId(),
    sharingManager.observeLinkedTechnicians(),
  ) { technicians, selfId, linked ->
    val self = technicians.find { it.id == selfId }
    val others = technicians.filter { it.id != selfId }
      .sortedBy { it.name.lowercase() }
    TechnicianListUiState(
      technicians = listOfNotNull(self) + others,
      linkedTechnicians = linked,
      selfId = selfId,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(),
    initialValue = TechnicianListUiState(),
  )
}
