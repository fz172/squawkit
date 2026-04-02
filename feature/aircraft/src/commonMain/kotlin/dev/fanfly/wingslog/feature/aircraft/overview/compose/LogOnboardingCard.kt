package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.generated.resources.add_first_maintenance_log
import wingslog.feature.aircraft.generated.resources.no_maintenance_logs_title
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@Composable
fun LogOnboardingCard(
  onAddLogClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
  ) {
    Column(
      modifier = Modifier.padding(Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
      ) {
        Icon(
          Icons.Default.History,
          contentDescription = null,
          modifier = Modifier.size(32.dp)
        )
        Text(
          text = stringResource(AircraftRes.string.no_maintenance_logs_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }

      Text(
        text = "Log your current airframe, engine, and prop times to start tracking maintenance intervals accurately. This will serve as your logbook's baseline.",
        style = MaterialTheme.typography.bodyLarge
      )

      Button(
        onClick = onAddLogClick,
        modifier = Modifier.fillMaxWidth().height(Spacing.buttonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
          contentColor = MaterialTheme.colorScheme.primaryContainer
        )
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.small))
        Text(
          text = stringResource(AircraftRes.string.add_first_maintenance_log).uppercase(),
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
