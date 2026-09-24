package dev.fanfly.wingslog.feature.developeroptions.stresstest.screen

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.developeroptions.stresstest.StressTestProgressStep
import dev.fanfly.wingslog.feature.developeroptions.stresstest.StressTestState
import dev.fanfly.wingslog.feature.developeroptions.stresstest.StressTestSummary
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.developeroptions.stresstest.generated.resources.Res
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_config_none
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_config_slot_count
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_progress_creating_log
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_progress_creating_squawk
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_progress_creating_task
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_progress_creating_technician
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_progress_creating_thing
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_progress_dismissing_squawk
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_progress_marking_squawk_addressed
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_addressed_squawks
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_components
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_dismissed_squawks
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_log_entries
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_log_entry_one
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_open_squawks
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_spec
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_squawks
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_tasks
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_technicians
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_summary_thing

@Composable
internal fun StressTestState.Running.displayText(): String = when (step) {
  StressTestProgressStep.CreatingThing -> stringResource(
    Res.string.stress_test_progress_creating_thing,
    subject.orEmpty(),
  )

  StressTestProgressStep.CreatingTechnician -> stringResource(
    Res.string.stress_test_progress_creating_technician,
    subject.orEmpty(),
  )

  StressTestProgressStep.CreatingTask -> stringResource(
    Res.string.stress_test_progress_creating_task,
    subject.orEmpty(),
  )

  StressTestProgressStep.CreatingSquawk -> stringResource(
    Res.string.stress_test_progress_creating_squawk,
    subject.orEmpty(),
  )

  StressTestProgressStep.CreatingLog -> stringResource(Res.string.stress_test_progress_creating_log)
  StressTestProgressStep.MarkingSquawkAddressed -> stringResource(
    Res.string.stress_test_progress_marking_squawk_addressed
  )

  StressTestProgressStep.DismissingSquawk -> stringResource(
    Res.string.stress_test_progress_dismissing_squawk
  )
}

@Composable
internal fun StressTestSummary.displayText(): String = buildList {
  add(
    stringResource(
      Res.string.stress_test_summary_thing,
      thingName,
      templateName
    )
  )
  specs.forEach { (label, value) ->
    add(stringResource(Res.string.stress_test_summary_spec, label, value))
  }
  val none = stringResource(Res.string.stress_test_config_none)
  add(
    stringResource(
      Res.string.stress_test_summary_components,
      components.map { (label, count) ->
        stringResource(Res.string.stress_test_config_slot_count, label, count)
      }
        .ifEmpty { listOf(none) }
        .joinToString(" · "),
    )
  )
  add("")
  add(
    stringResource(
      Res.string.stress_test_summary_technicians,
      technicianCount
    )
  )
  add(stringResource(Res.string.stress_test_summary_tasks, taskCount))
  add(
    stringResource(
      if (logCount == 1) Res.string.stress_test_summary_log_entry_one
      else Res.string.stress_test_summary_log_entries,
      logCount,
    )
  )
  add(stringResource(Res.string.stress_test_summary_squawks, squawkCount))
  add(
    stringResource(
      Res.string.stress_test_summary_open_squawks,
      openSquawkCount
    )
  )
  add(
    stringResource(
      Res.string.stress_test_summary_addressed_squawks,
      addressedSquawkCount
    )
  )
  add(
    stringResource(
      Res.string.stress_test_summary_dismissed_squawks,
      dismissedSquawkCount
    )
  )
}.joinToString("\n")
