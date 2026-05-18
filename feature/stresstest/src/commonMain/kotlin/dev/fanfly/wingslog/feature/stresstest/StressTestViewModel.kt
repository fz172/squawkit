package dev.fanfly.wingslog.feature.stresstest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StressTestState {
  data object Idle : StressTestState
  data class Running(
    val step: StressTestProgressStep,
    val subject: String? = null,
    val progress: Int,
    val total: Int,
  ) : StressTestState

  data class Done(val aircraftId: String, val summary: StressTestSummary) :
    StressTestState

  data class Error(val message: String?) : StressTestState
}

enum class StressTestProgressStep {
  CreatingAircraft,
  CreatingTechnician,
  CreatingTask,
  CreatingSquawk,
  CreatingLog,
  MarkingSquawkAddressed,
  DismissingSquawk,
}

data class StressTestSummary(
  val aircraftMake: String,
  val aircraftModel: String,
  val tailNumber: String,
  val serialNumber: String,
  val engineCount: Int,
  val engineModel: String,
  val technicianCount: Int,
  val taskCount: Int,
  val logCount: Int,
  val squawkCount: Int,
  val openSquawkCount: Int,
  val addressedSquawkCount: Int,
  val dismissedSquawkCount: Int,
)

class StressTestViewModel(
  private val fleetManager: FleetManager,
  private val technicianManager: TechnicianManager,
  private val taskDataManager: TaskDataManager,
  private val squawkManager: SquawkManager,
  private val logManager: MaintenanceLogManager,
) : ViewModel() {

  private val _config = MutableStateFlow(StressTestConfig())
  val config: StateFlow<StressTestConfig> = _config.asStateFlow()

  private val _state = MutableStateFlow<StressTestState>(StressTestState.Idle)
  val state: StateFlow<StressTestState> = _state.asStateFlow()

  fun setEngineCount(count: Int) {
    _config.value = _config.value.copy(engineCount = count)
  }

  fun setBladesPerEngine(count: Int) {
    _config.value = _config.value.copy(bladesPerEngine = count)
  }

  fun setSquawkCount(count: Int) {
    _config.value = _config.value.copy(squawkCount = count)
  }

  fun setTaskCount(count: Int) {
    _config.value = _config.value.copy(taskCount = count)
  }

  fun setLogCount(count: Int) {
    _config.value = _config.value.copy(logCount = count)
  }

  fun setTechnicianCount(count: Int) {
    _config.value = _config.value.copy(technicianCount = count)
  }

  fun reset() {
    _state.value = StressTestState.Idle
  }

  fun generate() {
    if (_state.value is StressTestState.Running) return
    viewModelScope.launch {
      runCatching {
        val config = _config.value
        val data = FakeDataGenerator.generate(config)
        val aircraftId = data.aircraft.id

        val totalSteps = 1 +
          data.technicians.size +
          data.tasks.size +
          data.squawks.size +
          data.logs.size +
          data.addressedSquawks.size +
          data.dismissedSquawks.size
        var step = 0

        fun progress(
          stepInfo: StressTestProgressStep,
          subject: String? = null
        ) {
          step++
          _state.value =
            StressTestState.Running(stepInfo, subject, step, totalSteps)
        }

        progress(
          StressTestProgressStep.CreatingAircraft,
          data.aircraft.tail_number
        )
        fleetManager.updateAircraft(data.aircraft)
          .getOrThrow()

        data.technicians.forEach { tech ->
          progress(StressTestProgressStep.CreatingTechnician, tech.name)
          technicianManager.updateTechnician(tech)
            .getOrThrow()
        }

        data.tasks.forEach { task ->
          progress(StressTestProgressStep.CreatingTask, task.title)
          taskDataManager.addTask(aircraftId, task)
            .getOrThrow()
        }

        data.squawks.forEach { squawk ->
          progress(StressTestProgressStep.CreatingSquawk, squawk.title)
          squawkManager.addSquawk(aircraftId, squawk)
            .getOrThrow()
        }

        data.logs.forEach { log ->
          progress(StressTestProgressStep.CreatingLog)
          logManager.addLog(aircraftId, log)
            .getOrThrow()
        }

        data.addressedSquawks.forEach { (squawkId, logId) ->
          progress(StressTestProgressStep.MarkingSquawkAddressed)
          squawkManager.markAddressed(aircraftId, listOf(squawkId), logId)
            .getOrThrow()
        }

        data.dismissedSquawks.forEach { (squawkId, reason) ->
          progress(StressTestProgressStep.DismissingSquawk)
          squawkManager.dismissSquawk(aircraftId, squawkId, reason)
            .getOrThrow()
        }

        val openCount =
          data.squawks.size - data.addressedSquawks.size - data.dismissedSquawks.size
        val summary = StressTestSummary(
          aircraftMake = data.aircraft.make,
          aircraftModel = data.aircraft.model,
          tailNumber = data.aircraft.tail_number,
          serialNumber = data.aircraft.serial,
          engineCount = data.aircraft.engine.size,
          engineModel = data.aircraft.engine.firstOrNull()?.model.orEmpty(),
          technicianCount = data.technicians.size,
          taskCount = data.tasks.size,
          logCount = data.logs.size,
          squawkCount = data.squawks.size,
          openSquawkCount = openCount.coerceAtLeast(0),
          addressedSquawkCount = data.addressedSquawks.size,
          dismissedSquawkCount = data.dismissedSquawks.size,
        )

        _state.value = StressTestState.Done(aircraftId, summary)
      }.onFailure { e ->
        _state.value = StressTestState.Error(e.message)
      }
    }
  }
}
