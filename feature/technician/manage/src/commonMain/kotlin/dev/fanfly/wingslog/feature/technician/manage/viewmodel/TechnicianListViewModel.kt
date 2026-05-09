package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TechnicianListUiState(
  val technicians: List<Technician> = emptyList(),
  val selfId: String? = null,
)

class TechnicianListViewModel(
  technicianManager: TechnicianManager,
) : ViewModel() {

  val uiState: StateFlow<TechnicianListUiState> = combine(
    technicianManager.observeTechnicians(),
    technicianManager.observeSelfId(),
  ) { technicians, selfId ->
    val self = technicians.find { it.id == selfId }
    val others = technicians.filter { it.id != selfId }.sortedBy { it.name.lowercase() }
    TechnicianListUiState(
      technicians = listOfNotNull(self) + others,
      selfId = selfId,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(),
    initialValue = TechnicianListUiState(),
  )
}
