package dev.fanfly.wingslog.feature.squawk.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.openAog
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Provides the squawk slice of the Overview (`DASHBOARD`) section: down/AOG squawks for the
 * Needs Attention banner and the newest 3 open squawks for the wide-tier rail.
 */
class SquawkOverviewViewModel(
  squawkManager: SquawkManager,
  thingId: String,
) : ViewModel() {

  val uiState: StateFlow<SquawkOverviewUiState> =
    squawkManager.observeSquawks(thingId)
      .distinctUntilChanged()
      .map { squawks ->
        val withStatus = squawks.map { it.toWithStatus() }
        SquawkOverviewUiState(
          loaded = true,
          aogSquawks = withStatus.openAog(),
          openPreview = withStatus
            .filter { it.status == SquawkStatus.OPEN }
            .sortedByDescending { it.squawk.created_at?.getEpochSecond() ?: 0L }
            .take(PREVIEW_LIMIT),
        )
      }
      .catch { emit(SquawkOverviewUiState(loaded = true)) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, SquawkOverviewUiState())

  private companion object {
    const val PREVIEW_LIMIT = 3
  }
}
