package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun InspectionCard(
  title: String,
  status: String,
  icon: ImageVector,
  statusColor: Color,
  isOverdue: Boolean = false,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val containerColor = if (isOverdue) {
    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }

  val borderColor = if (isOverdue) {
    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
  } else {
    Color.Transparent
  }

  Card(
    onClick = onClick,
    modifier = modifier.fillMaxHeight().heightIn(min = 88.dp),
    colors = CardDefaults.cardColors(
      containerColor = containerColor
    ),
    border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
    shape = RoundedCornerShape(Spacing.cardCornerRadius)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(Spacing.large),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.Start
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(Spacing.small))
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
        )
      }
      Spacer(Modifier.fillMaxWidth().height(Spacing.small))
      Text(
        text = status,
        style = MaterialTheme.typography.labelMedium,
        color = if (isOverdue) MaterialTheme.colorScheme.error else statusColor,
        fontWeight = FontWeight.ExtraBold
      )
    }
  }
}

@Preview
@Composable
fun PreviewInspectionCard() = InspectionCard(
  title = "100 Hr",
  status = "Overdue (was Dec 12, 2024)",
  icon = Icons.Default.Schedule,
  statusColor = Color(0xFF8B5E00),
  modifier = Modifier.fillMaxWidth()
)