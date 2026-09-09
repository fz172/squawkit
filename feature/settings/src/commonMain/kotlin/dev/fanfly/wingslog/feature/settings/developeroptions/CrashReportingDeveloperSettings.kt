package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.Res
import wingslog.feature.settings.generated.resources.developer_options_test_crash_action
import wingslog.feature.settings.generated.resources.developer_options_test_crash_subtitle
import wingslog.feature.settings.generated.resources.developer_options_test_crash_title
import wingslog.feature.settings.generated.resources.developer_options_test_non_fatal_action
import wingslog.feature.settings.generated.resources.developer_options_test_non_fatal_subtitle
import wingslog.feature.settings.generated.resources.developer_options_test_non_fatal_title

/**
 * The only way to confirm Crashlytics is actually reporting from a given build and account —
 * a crash-reporting integration that has never delivered a report is indistinguishable from one
 * that is silently broken.
 */
@Composable
fun CrashReportingDeveloperSettings(
  onRecordTestNonFatal: () -> Unit,
  onForceTestCrash: () -> Unit,
) {
  ActionRow(
    title = stringResource(Res.string.developer_options_test_non_fatal_title),
    subtitle = stringResource(Res.string.developer_options_test_non_fatal_subtitle),
    action = stringResource(Res.string.developer_options_test_non_fatal_action),
    onClick = onRecordTestNonFatal,
  )
  ActionRow(
    title = stringResource(Res.string.developer_options_test_crash_title),
    subtitle = stringResource(Res.string.developer_options_test_crash_subtitle),
    action = stringResource(Res.string.developer_options_test_crash_action),
    onClick = onForceTestCrash,
  )
}

@Composable
private fun ActionRow(
  title: String,
  subtitle: String,
  action: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    OutlinedButton(onClick = onClick) {
      Text(action)
    }
  }
}
