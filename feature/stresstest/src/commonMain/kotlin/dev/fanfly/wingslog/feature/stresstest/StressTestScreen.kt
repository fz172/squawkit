package dev.fanfly.wingslog.feature.stresstest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.stresstest.generated.resources.Res
import wingslog.feature.stresstest.generated.resources.stress_test_complete
import wingslog.feature.stresstest.generated.resources.stress_test_config_aircraft
import wingslog.feature.stresstest.generated.resources.stress_test_config_blades_per_engine
import wingslog.feature.stresstest.generated.resources.stress_test_config_engines
import wingslog.feature.stresstest.generated.resources.stress_test_config_log_entries
import wingslog.feature.stresstest.generated.resources.stress_test_config_records
import wingslog.feature.stresstest.generated.resources.stress_test_config_squawks
import wingslog.feature.stresstest.generated.resources.stress_test_config_tasks
import wingslog.feature.stresstest.generated.resources.stress_test_config_technicians
import wingslog.feature.stresstest.generated.resources.stress_test_description
import wingslog.feature.stresstest.generated.resources.stress_test_generate
import wingslog.feature.stresstest.generated.resources.stress_test_regenerate
import wingslog.feature.stresstest.generated.resources.stress_test_title
import wingslog.feature.stresstest.generated.resources.stress_test_working

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StressTestScreen(
  navController: NavController,
  viewModel: StressTestViewModel = koinViewModel(),
) {
  val config by viewModel.config.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = stringResource(Res.string.stress_test_title),
        onBackClick = { navController.popBackStack() },
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
          .padding(innerPadding)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {

      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.BugReport,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(end = Spacing.small),
        )
        Column {
          Text(
            text = stringResource(Res.string.stress_test_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(Res.string.stress_test_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      val isRunning = state is StressTestState.Running
      val isDone = state is StressTestState.Done
      val isError = state is StressTestState.Error
      val isIdle = state is StressTestState.Idle

      AnimatedVisibility(visible = isIdle || isError) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {

          ConfigSection(title = stringResource(Res.string.stress_test_config_aircraft)) {
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
          }

          ConfigSection(title = stringResource(Res.string.stress_test_config_records)) {
            SliderRow(
              label = stringResource(Res.string.stress_test_config_squawks),
              value = config.squawkCount,
              range = 2..15,
              onValueChange = { viewModel.setSquawkCount(it) },
            )
            SliderRow(
              label = stringResource(Res.string.stress_test_config_tasks),
              value = config.taskCount,
              range = 5..20,
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
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(Spacing.cardCornerRadius),
            ) {
              Text(
                text = "Error: ${(state as StressTestState.Error).message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
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
              imageVector = Icons.Default.AirplanemodeActive,
              contentDescription = null,
              modifier = Modifier.padding(end = Spacing.small),
            )
            Text(stringResource(Res.string.stress_test_generate))
          }
        }
      }

      AnimatedVisibility(visible = isRunning) {
        val running = state as? StressTestState.Running
        Column(
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Spacer(Modifier.height(Spacing.large))
          CircularProgressIndicator(modifier = Modifier.size(Spacing.massive))
          Text(
            text = running?.step
              ?: stringResource(Res.string.stress_test_working),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (running != null && running.total > 0) {
            val progress = running.progress.toFloat() / running.total
            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              text = "${running.progress} / ${running.total}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(Spacing.large))
        }
      }

      AnimatedVisibility(visible = isDone) {
        val done = state as? StressTestState.Done
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          ) {
            Box(
              modifier = Modifier
                  .size(Spacing.extraLarge)
                  .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(Spacing.large),
              )
            }
            Text(
              text = stringResource(Res.string.stress_test_complete),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(Spacing.cardCornerRadius),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = done?.summary ?: "",
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
              ),
              modifier = Modifier.padding(Spacing.medium),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          OutlinedButton(
            onClick = viewModel::reset,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Spacing.buttonCornerRadius),
          ) {
            Text(stringResource(Res.string.stress_test_regenerate))
          }
        }
      }
    }
  }
}

@Composable
private fun ConfigSection(
  title: String,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(bottom = Spacing.extraSmall),
    )
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
      shape = RoundedCornerShape(Spacing.cardCornerRadius),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        content()
      }
    }
  }
}

@Composable
private fun SliderRow(
  label: String,
  value: Int,
  range: IntRange,
  onValueChange: (Int) -> Unit,
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = value.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(
      value = value.toFloat(),
      onValueChange = { onValueChange(it.toInt()) },
      valueRange = range.first.toFloat()..range.last.toFloat(),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun StepperRow(
  label: String,
  value: Int,
  range: IntRange,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      OutlinedButton(
        onClick = onDecrement,
        enabled = value > range.first,
        shape = RoundedCornerShape(Spacing.chipCornerRadius),
        contentPadding = PaddingValues(
          horizontal = Spacing.large,
          vertical = Spacing.small
        ),
      ) {
        Text("−", style = MaterialTheme.typography.titleMedium)
      }
      Text(
        text = value.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.small),
      )
      OutlinedButton(
        onClick = onIncrement,
        enabled = value < range.last,
        shape = RoundedCornerShape(Spacing.chipCornerRadius),
        contentPadding = PaddingValues(
          horizontal = Spacing.large,
          vertical = Spacing.small
        ),
      ) {
        Text("+", style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}
