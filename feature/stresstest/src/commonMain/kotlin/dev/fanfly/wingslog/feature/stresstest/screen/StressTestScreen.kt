package dev.fanfly.wingslog.feature.stresstest.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import dev.fanfly.wingslog.core.ui.bar.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.layout.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.stresstest.StressTestState
import dev.fanfly.wingslog.feature.stresstest.StressTestViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.stresstest.generated.resources.Res
import wingslog.feature.stresstest.generated.resources.stress_test_complete
import wingslog.feature.stresstest.generated.resources.stress_test_description
import wingslog.feature.stresstest.generated.resources.stress_test_progress_count
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
      ConstrainedTopBar(ContentWidth.Form) {
        WingsLogTopAppBar(
          title = stringResource(Res.string.stress_test_title),
          onBackClick = { navController.popBackStack() },
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .constrainedContentWidth(ContentWidth.Form)
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
          StressTestConfigForm(
            config = config,
            state = state,
            isError = isError,
            viewModel = viewModel,
          )
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
              text = running?.displayText()
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
                text = stringResource(
                  Res.string.stress_test_progress_count,
                  running.progress,
                  running.total,
                ),
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
                text = done?.summary?.displayText() ?: "",
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
}
