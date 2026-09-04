package dev.fanfly.wingslog.feature.stresstest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.slotLabel
import dev.fanfly.wingslog.core.template.specLabel
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.stresstest.fixtures.FakeDataPools
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.ThingTemplate
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

  data class Done(val thingId: String, val summary: StressTestSummary) :
    StressTestState

  data class Error(val message: String?) : StressTestState
}

enum class StressTestProgressStep {
  CreatingThing,
  CreatingTechnician,
  CreatingTask,
  CreatingSquawk,
  CreatingLog,
  MarkingSquawkAddressed,
  DismissingSquawk,
}

/**
 * What was written, in the shape of the preset that was picked: a car reports a VIN and four
 * tires where an aeroplane reports a tail number and its engines. Labels come from the template.
 */
data class StressTestSummary(
  val thingName: String,
  val templateName: String,
  /** Template spec label to the value generated for it, blanks dropped. */
  val specs: List<Pair<String, String>>,
  /** Top-level slot label to how many of that slot the thing carries. */
  val components: List<Pair<String, Int>>,
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

  /**
   * Switching preset also clamps the task and squawk counts to what its pool holds: the generator
   * caps there anyway, and a slider left at 20 tasks for a preset with two would promise records
   * that never arrive.
   */
  fun setTemplateId(id: String) {
    val pool = FakeDataPools.forTemplate(templateFor(id))
    _config.value = _config.value.copy(
      templateId = id,
      taskCount = _config.value.taskCount.coerceAtMost(pool.tasks.size),
      squawkCount = _config.value.squawkCount.coerceAtMost(pool.squawks.size),
    )
  }

  /** The most distinct tasks and squawks the selected preset can supply. */
  fun poolLimits(templateId: String): PoolLimits {
    val pool = FakeDataPools.forTemplate(templateFor(templateId))
    return PoolLimits(tasks = pool.tasks.size, squawks = pool.squawks.size)
  }

  fun templateFor(templateId: String): ThingTemplate =
    CanonicalTemplates.ALL.firstOrNull { it.id == templateId } ?: AirplaneTemplate.TEMPLATE

  fun setDnaFromANewerBuild(value: Boolean) {
    _config.value = _config.value.copy(dnaFromANewerBuild = value)
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
        val thingId = data.thing.id

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

        progress(StressTestProgressStep.CreatingThing, data.thing.name)
        fleetManager.updateThing(data.thing)
          .getOrThrow()

        data.technicians.forEach { tech ->
          progress(StressTestProgressStep.CreatingTechnician, tech.name)
          technicianManager.updateTechnician(tech)
            .getOrThrow()
        }

        data.tasks.forEach { task ->
          progress(StressTestProgressStep.CreatingTask, task.title)
          taskDataManager.addTask(thingId, task)
            .getOrThrow()
        }

        data.squawks.forEach { squawk ->
          progress(StressTestProgressStep.CreatingSquawk, squawk.title)
          squawkManager.addSquawk(thingId, squawk)
            .getOrThrow()
        }

        data.logs.forEach { log ->
          progress(StressTestProgressStep.CreatingLog)
          logManager.addLog(thingId, log)
            .getOrThrow()
        }

        data.addressedSquawks.forEach { (squawkId, logId) ->
          progress(StressTestProgressStep.MarkingSquawkAddressed)
          squawkManager.markAddressed(thingId, listOf(squawkId), logId)
            .getOrThrow()
        }

        data.dismissedSquawks.forEach { (squawkId, reason) ->
          progress(StressTestProgressStep.DismissingSquawk)
          squawkManager.dismissSquawk(thingId, squawkId, reason)
            .getOrThrow()
        }

        val openCount =
          data.squawks.size - data.addressedSquawks.size - data.dismissedSquawks.size
        val template = templateFor(config.templateId)
        val summary = StressTestSummary(
          thingName = data.thing.name,
          templateName = template.display_name,
          specs = data.thing.spec
            .filter { it.value_.isNotBlank() }
            .map { template.specLabel(it.key, it.key) to it.value_ },
          components = data.thing.components
            .groupingBy { it.slot_key }
            .eachCount()
            .map { (key, count) -> template.slotLabel(key, key) to count },
          technicianCount = data.technicians.size,
          taskCount = data.tasks.size,
          logCount = data.logs.size,
          squawkCount = data.squawks.size,
          openSquawkCount = openCount.coerceAtLeast(0),
          addressedSquawkCount = data.addressedSquawks.size,
          dismissedSquawkCount = data.dismissedSquawks.size,
        )

        _state.value = StressTestState.Done(thingId, summary)
      }.onFailure { e ->
        _state.value = StressTestState.Error(e.message)
      }
    }
  }
}

data class PoolLimits(val tasks: Int, val squawks: Int)
