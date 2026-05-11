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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.aog_alert_subtitle
import wingslog.feature.squawk.sharedassets.generated.resources.aog_alert_title
import wingslog.feature.squawk.sharedassets.generated.resources.view_squawks

@Composable
fun AogAlertSection(
  aogSquawks: List<Squawk>,
  onViewSquawksClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (aogSquawks.isEmpty()) return

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
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
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(28.dp),
        )
        Text(
          text = stringResource(Res.string.aog_alert_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.error,
        )
        Text(
          text = stringResource(Res.string.aog_alert_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

      Column(
        modifier = Modifier.padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Text(
          text = "AOG",
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
          text = stringResource(Res.string.view_squawks),
          color = MaterialTheme.colorScheme.error,
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
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onViewClick)
      .padding(vertical = Spacing.tiny),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier = Modifier
        .padding(top = 5.dp)
        .size(6.dp)
        .background(MaterialTheme.colorScheme.error, CircleShape)
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
