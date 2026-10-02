package dev.fanfly.wingslog.feature.developeroptions.aiecho

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsExtra
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import kotlin.time.DurationUnit
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.developeroptions.aiecho.generated.resources.Res
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_failed
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_header
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_idle
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_mismatch
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_no_thing
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_passed
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_refused
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_run_action
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_step_closing
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_step_queued
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_step_running
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_step_starting
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_timed_out
import wingslog.feature.developeroptions.aiecho.generated.resources.ai_echo_title

/**
 * The AI backend's Developer Options section: one button that runs [AiEchoRoundTrip] and shows
 * each step, then the outcome. Codes are shown as the backend names them; this is a developer's
 * tool, and the name is what the logs and the design doc use.
 */
class AiEchoDeveloperOptionsExtra(
  private val capability: AppCapability,
  private val roundTrip: AiEchoRoundTrip,
) : DeveloperOptionsExtra {

  override val order: Int = 600

  override fun isAvailable(): Boolean = capability.isDeveloperOptionsSupported

  @Composable
  override fun Content(onNavigate: (route: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<EchoStep?>(null) }
    var outcome by remember { mutableStateOf<EchoOutcome?>(null) }
    var running by remember { mutableStateOf(false) }

    Spacer(Modifier.height(Spacing.extraLarge))
    Text(
      text = stringResource(Res.string.ai_echo_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(bottom = Spacing.small),
    )
    HorizontalDivider()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = Spacing.medium),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = Spacing.medium)) {
        Text(
          text = stringResource(Res.string.ai_echo_title),
          style = MaterialTheme.typography.bodyLarge,
        )
        Text(
          text = if (running) step.label() else outcome.label(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OutlinedButton(
        enabled = !running,
        onClick = {
          running = true
          step = null
          scope.launch {
            outcome = roundTrip.run { step = it }
            running = false
          }
        },
      ) {
        Text(stringResource(Res.string.ai_echo_run_action))
      }
    }
  }
}

@Composable
private fun EchoStep?.label(): String = when (this) {
  null, EchoStep.Starting -> stringResource(Res.string.ai_echo_step_starting)
  is EchoStep.Running -> when {
    status == AiJobStatus.AI_JOB_STATUS_QUEUED -> stringResource(Res.string.ai_echo_step_queued)
    else -> stringResource(Res.string.ai_echo_step_running, stage ?: status?.name.orEmpty())
  }
  EchoStep.Closing -> stringResource(Res.string.ai_echo_step_closing)
}

@Composable
private fun EchoOutcome?.label(): String = when (this) {
  null -> stringResource(Res.string.ai_echo_idle)
  is EchoOutcome.Passed -> stringResource(Res.string.ai_echo_passed, elapsed.toString(DurationUnit.SECONDS, 1))
  is EchoOutcome.Refused -> stringResource(Res.string.ai_echo_refused, reason.wire ?: reason.name)
  is EchoOutcome.Failed -> stringResource(Res.string.ai_echo_failed, reason.wire ?: reason.name)
  is EchoOutcome.Mismatch -> stringResource(Res.string.ai_echo_mismatch)
  EchoOutcome.TimedOut -> stringResource(Res.string.ai_echo_timed_out)
  EchoOutcome.NoThing -> stringResource(Res.string.ai_echo_no_thing)
}
