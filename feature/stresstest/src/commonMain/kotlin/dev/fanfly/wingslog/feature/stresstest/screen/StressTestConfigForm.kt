package dev.fanfly.wingslog.feature.stresstest.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.stresstest.StressTestConfig
import dev.fanfly.wingslog.feature.stresstest.StressTestState
import dev.fanfly.wingslog.feature.stresstest.StressTestViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.stresstest.generated.resources.Res
import wingslog.feature.stresstest.generated.resources.stress_test_config_blades_per_engine
import wingslog.feature.stresstest.generated.resources.stress_test_config_engines
import wingslog.feature.stresstest.generated.resources.stress_test_config_future_dna
import wingslog.feature.stresstest.generated.resources.stress_test_config_log_entries
import wingslog.feature.stresstest.generated.resources.stress_test_config_records
import wingslog.feature.stresstest.generated.resources.stress_test_config_squawks
import wingslog.feature.stresstest.generated.resources.stress_test_config_tasks
import wingslog.feature.stresstest.generated.resources.stress_test_config_technicians
import wingslog.feature.stresstest.generated.resources.stress_test_config_thing
import wingslog.feature.stresstest.generated.resources.stress_test_error_message
import wingslog.feature.stresstest.generated.resources.stress_test_generate
import wingslog.feature.stresstest.generated.resources.stress_test_unknown_error

/**
 * Everything the run is configured with, shown only while it is idle or has failed: the template
 * and its knobs, the record counts, the last error, and the button that starts it.
 */
@Composable
internal fun StressTestConfigForm(
  config: StressTestConfig,
  state: StressTestState,
  isError: Boolean,
  viewModel: StressTestViewModel,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
  // The template drives everything below it, so it comes first. Airplane is the only
  // preset with knobs of its own; every other thing is built from what its template
  // declares, and the preview says what that will be.
  val template = viewModel.templateFor(config.templateId)
  val limits = viewModel.poolLimits(config.templateId)
  ConfigSection(title = stringResource(Res.string.stress_test_config_thing)) {
    TemplateRow(
      selectedId = config.templateId,
      onSelect = { viewModel.setTemplateId(it) },
    )
    if (template.id == AirplaneTemplate.ID) {
      StepperRow(
        label = stringResource(Res.string.stress_test_config_engines),
        value = config.engineCount,
        range = 1..2,
        onDecrement = { viewModel.setEngineCount(config.engineCount - 1) },
        onIncrement = { viewModel.setEngineCount(config.engineCount + 1) },
      )
      StepperRow(
        label = stringResource(Res.string.stress_test_config_blades_per_engine),
        value = config.bladesPerEngine,
        range = 2..4,
        onDecrement = { viewModel.setBladesPerEngine(config.bladesPerEngine - 1) },
        onIncrement = { viewModel.setBladesPerEngine(config.bladesPerEngine + 1) },
      )
    } else {
      TemplatePreview(template)
    }
    SwitchRow(
      label = stringResource(Res.string.stress_test_config_future_dna),
      checked = config.dnaFromANewerBuild,
      onCheckedChange = { viewModel.setDnaFromANewerBuild(it) },
    )
  }

  // Tasks and squawks stop at the preset's pool: the generator never makes more distinct
  // records than it has samples for, so the slider should not offer them either.
  ConfigSection(title = stringResource(Res.string.stress_test_config_records)) {
    SliderRow(
      label = stringResource(Res.string.stress_test_config_squawks),
      value = config.squawkCount,
      range = 1..limits.squawks,
      onValueChange = { viewModel.setSquawkCount(it) },
    )
    SliderRow(
      label = stringResource(Res.string.stress_test_config_tasks),
      value = config.taskCount,
      range = 1..limits.tasks,
      onValueChange = { viewModel.setTaskCount(it) },
    )
    SliderRow(
      label = stringResource(Res.string.stress_test_config_log_entries),
      value = config.logCount,
      range = 10..100,
      onValueChange = { viewModel.setLogCount(it) },
    )
    SliderRow(
      label = stringResource(Res.string.stress_test_config_technicians),
      value = config.technicianCount,
      range = 1..5,
      onValueChange = { viewModel.setTechnicianCount(it) },
    )
  }

  if (isError) {
    val error = state as StressTestState.Error
    val colors = MaterialTheme.statusColors.critical
    Surface(
      color = colors.container,
      shape = RoundedCornerShape(Spacing.cardCornerRadius),
    ) {
      Text(
        text = stringResource(
          Res.string.stress_test_error_message,
          error.message
            ?: stringResource(Res.string.stress_test_unknown_error),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onContainer,
        modifier = Modifier.padding(Spacing.medium),
      )
    }
  }

  Button(
    onClick = viewModel::generate,
    modifier = Modifier.fillMaxWidth()
      .height(Spacing.buttonHeight),
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
  ) {
    Icon(
      imageVector = Icons.Default.Build,
      contentDescription = null,
      modifier = Modifier.padding(end = Spacing.small),
    )
    Text(stringResource(Res.string.stress_test_generate))
  }
}
}
