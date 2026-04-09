package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TechnicianListViewModel(
  private val technicianManager: TechnicianManager
) : ViewModel() {

  private val logger = Logger.withTag("TechnicianListViewModel")

  val technicians: StateFlow<List<Technician>> = technicianManager.observeTechnicians()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  fun deleteTechnician(id: String) {
    viewModelScope.launch {
      val result = technicianManager.deleteTechnician(id)
      result.onFailure {
        logger.e(it) { "Failed to delete technician: $id" }
      }
    }
  }
}
