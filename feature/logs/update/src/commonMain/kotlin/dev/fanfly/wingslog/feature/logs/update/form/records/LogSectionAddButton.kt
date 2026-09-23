package dev.fanfly.wingslog.feature.logs.update.form.records

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** The "+ Add" control shown on a records section header (Squawks Addressed / Tasks Completed). */
@Composable
internal fun LogSectionAddButton(onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    contentPadding = PaddingValues(
      horizontal = Spacing.medium,
      vertical = Spacing.extraSmall
    ),
  ) {
    Icon(
      Icons.Default.Add,
      contentDescription = null,
      modifier = Modifier.width(Spacing.large)
    )
    Spacer(Modifier.width(Spacing.extraSmall))
    Text(
      stringResource(CoreRes.string.add),
      style = MaterialTheme.typography.labelMedium
    )
  }
}
