package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.dashboard.generated.resources.Res
import wingslog.feature.tasks.dashboard.generated.resources.suggestions_ready_many
import wingslog.feature.tasks.dashboard.generated.resources.suggestions_ready_one
import wingslog.feature.tasks.dashboard.generated.resources.suggestions_ready_from
import wingslog.feature.tasks.dashboard.generated.resources.suggestions_review

/**
 * "3 more suggestions ready · From Rotax-915-Line-Maint.pdf", with *Review* (1f): a slim card above
 * the list for an answer that came in while the user was elsewhere.
 */
@Composable
internal fun ReadySuggestionsCard(ready: ReadySuggestions, onReview: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(start = Spacing.medium, end = Spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
        Icons.Default.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(vertical = Spacing.medium),
      ) {
        Text(
          text = if (ready.count == 1) {
            stringResource(Res.string.suggestions_ready_one)
          } else {
            stringResource(Res.string.suggestions_ready_many, ready.count)
          },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        ready.document?.let {
          Text(
            text = stringResource(Res.string.suggestions_ready_from, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      TextButton(onClick = onReview) { Text(stringResource(Res.string.suggestions_review)) }
    }
  }
}
