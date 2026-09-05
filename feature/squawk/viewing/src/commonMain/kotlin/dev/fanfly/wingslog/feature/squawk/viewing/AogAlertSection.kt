package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.thing.Squawk
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.view_squawks

@Composable
fun AogAlertSection(
  aogSquawks: List<Squawk>,
  onViewSquawksClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (aogSquawks.isEmpty()) return
  val blocking = MaterialTheme.statusColors.blocking

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      blocking.accent.copy(alpha = 0.5f)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Spacing.large, vertical = Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Icon(
          imageVector = Icons.Default.FlightLand,
          contentDescription = null,
          tint = blocking.accent,
          modifier = Modifier.size(Spacing.huge),
        )
        Text(
          text = LexiconFormatter.titleCase(LocalThingLexicon.current.down_status_long),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = blocking.accent,
        )
        // Per-template sentence; a Thing frozen on an older template has none and shows no line.
        val hint = LocalThingLexicon.current.down_alert_hint
        if (hint.isNotBlank()) {
          Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

      Column(
        modifier = Modifier.padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Text(
          text = LexiconFormatter.titleCase(LocalThingLexicon.current.down_status),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 1.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          aogSquawks.forEach { squawk ->
            AogSquawkRow(squawk = squawk, onViewClick = onViewSquawksClick)
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

      TextButton(
        onClick = onViewSquawksClick,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Spacing.medium),
      ) {
        Text(
          text = stringResource(
            Res.string.view_squawks,
            LexiconFormatter.titleCasePlural(LocalThingLexicon.current.squawkNoun),
          ),
          color = blocking.accent,
        )
      }
    }
  }
}

@Composable
private fun AogSquawkRow(
  squawk: Squawk,
  onViewClick: () -> Unit,
) {
  val blocking = MaterialTheme.statusColors.blocking
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onViewClick)
      .padding(vertical = Spacing.extraSmall),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier = Modifier
        .padding(top = Spacing.extraSmall)
        .size(Spacing.extraSmall)
        .background(blocking.accent, CircleShape)
    )
    Text(
      text = squawk.title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}
