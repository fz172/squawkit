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
    data class Running(val step: String, val progress: Int, val total: Int) : StressTestState
    data class Done(val aircraftId: String, val summary: String) : StressTestState
    data class Error(val message: String) : StressTestState
}

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

    fun setEngineCount(count: Int) { _config.value = _config.value.copy(engineCount = count) }
    fun setBladesPerEngine(count: Int) { _config.value = _config.value.copy(bladesPerEngine = count) }
    fun setSquawkCount(count: Int) { _config.value = _config.value.copy(squawkCount = count) }
    fun setTaskCount(count: Int) { _config.value = _config.value.copy(taskCount = count) }
    fun setLogCount(count: Int) { _config.value = _config.value.copy(logCount = count) }
    fun setTechnicianCount(count: Int) { _config.value = _config.value.copy(technicianCount = count) }

    fun reset() { _state.value = StressTestState.Idle }

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

                fun progress(label: String) {
                    step++
                    _state.value = StressTestState.Running(label, step, totalSteps)
                }

                progress("Creating aircraft ${data.aircraft.tail_number}…")
                fleetManager.updateAircraft(data.aircraft).getOrThrow()

                data.technicians.forEach { tech ->
                    progress("Creating technician ${tech.name}…")
                    technicianManager.updateTechnician(tech).getOrThrow()
                }

                data.tasks.forEach { task ->
                    progress("Creating task: ${task.title}…")
                    taskDataManager.addTask(aircraftId, task).getOrThrow()
                }

                data.squawks.forEach { squawk ->
                    progress("Creating squawk: ${squawk.title}…")
                    squawkManager.addSquawk(aircraftId, squawk).getOrThrow()
                }

                data.logs.forEach { log ->
                    progress("Creating log entry…")
                    logManager.addLog(aircraftId, log).getOrThrow()
                }

                data.addressedSquawks.forEach { (squawkId, logId) ->
                    progress("Marking squawk addressed…")
                    squawkManager.markAddressed(aircraftId, listOf(squawkId), logId).getOrThrow()
                }

                data.dismissedSquawks.forEach { (squawkId, reason) ->
                    progress("Dismissing squawk…")
                    squawkManager.dismissSquawk(aircraftId, squawkId, reason).getOrThrow()
                }

                val openCount = data.squawks.size - data.addressedSquawks.size - data.dismissedSquawks.size
                val summary = buildString {
                    appendLine("Aircraft: ${data.aircraft.make} ${data.aircraft.model} (${data.aircraft.tail_number})")
                    appendLine("Tail: ${data.aircraft.tail_number} · S/N: ${data.aircraft.serial}")
                    appendLine("Engines: ${data.aircraft.engine.size} × ${data.aircraft.engine.firstOrNull()?.model ?: "-"}")
                    appendLine()
                    appendLine("${data.technicians.size} technician(s) created")
                    appendLine("${data.tasks.size} task(s) created")
                    appendLine("${data.logs.size} log entr${if (data.logs.size == 1) "y" else "ies"} created (span: 4 years)")
                    appendLine("${data.squawks.size} squawk(s) created:")
                    appendLine("  • ${openCount.coerceAtLeast(0)} open")
                    appendLine("  • ${data.addressedSquawks.size} addressed")
                    append("  • ${data.dismissedSquawks.size} dismissed")
                }

                _state.value = StressTestState.Done(aircraftId, summary)
            }.onFailure { e ->
                _state.value = StressTestState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
