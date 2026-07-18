package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.hours_abbr_value
import wingslog.feature.logs.viewing.generated.resources.log_squawk_count_one
import wingslog.feature.logs.viewing.generated.resources.log_squawk_count_plural
import wingslog.feature.logs.viewing.generated.resources.log_task_count_one
import wingslog.feature.logs.viewing.generated.resources.log_task_count_plural
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import kotlin.time.Instant
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes

@Composable
fun MaintenanceLogCard(
  log: MaintenanceLog,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val dateStr = log.timestamp?.toLocalDate()
    ?.toDisplayFormat()
    ?: stringResource(SharedRes.string.unknown_date)
  val tacHours = log.primaryHours()

  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = Spacing.large,
          vertical = Spacing.large
        ),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      // Top row: component badge | tach hours + chevron
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ComponentTypeBadge(log.component_type)
        Spacer(Modifier.weight(1f))
        if (tacHours > 0.0) {
          Text(
            text = stringResource(
              MaintenanceRes.string.hours_abbr_value,
              tacHours.formatToOneDecimalPlace(),
            ),
            style = WingslogTypography.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.width(Spacing.small))
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Work description — full text, no truncation
      Text(
        text = log.work_description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(
          alpha = 0.3f
        )
      )

      // Footer: date | task count + technician
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = dateStr,
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        val taskCount = log.inspection_ids.size
        val squawkCount = log.squawk_ids.size
        if (taskCount > 0) {
          val taskLabel =
            if (taskCount == 1) stringResource(MaintenanceRes.string.log_task_count_one)
            else stringResource(
              MaintenanceRes.string.log_task_count_plural,
              taskCount
            )
          Text(
            text = taskLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (squawkCount > 0) {
          if (taskCount > 0) Spacer(Modifier.width(Spacing.medium))
          val squawkLabel =
            if (squawkCount == 1) stringResource(MaintenanceRes.string.log_squawk_count_one)
            else stringResource(
              MaintenanceRes.string.log_squawk_count_plural,
              squawkCount
            )
          Text(
            text = squawkLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        val techName = log.technician?.name?.takeIf { it.isNotBlank() }
        if (techName != null) {
          if (taskCount > 0 || squawkCount > 0) Spacer(Modifier.width(Spacing.medium))
          Text(
            text = techName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private data class BadgeScheme(
  val background: Color,
  val contentColor: Color,
)

@Composable
private fun badgeSchemeFor(type: ComponentType): BadgeScheme = when (type) {
  ComponentType.COMPONENT_ENGINE -> BadgeScheme(
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.onPrimaryContainer,
  )

  ComponentType.COMPONENT_AIRFRAME -> BadgeScheme(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.onSurfaceVariant,
  )

  ComponentType.COMPONENT_PROPELLER -> BadgeScheme(
    MaterialTheme.colorScheme.secondaryContainer,
    MaterialTheme.colorScheme.onSecondaryContainer,
  )

  else -> BadgeScheme(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
internal fun ComponentTypeBadge(
  type: ComponentType,
  modifier: Modifier = Modifier,
) {
  val scheme = badgeSchemeFor(type)
  Box(
    modifier = modifier
      .background(
        color = scheme.background,
        shape = RoundedCornerShape(Spacing.badgeCornerRadius)
      )
      .padding(
        horizontal = Spacing.small,
        vertical = Spacing.extraSmall
      ),
  ) {
    Text(
      text = type.displayName()
        .uppercase(),
      color = scheme.contentColor,
      fontSize = 10.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 0.6.sp,
      lineHeight = 14.sp,
    )
  }
}

private fun MaintenanceLog.primaryHours(): Double = when (component_type) {
  ComponentType.COMPONENT_ENGINE -> engine_hour
  ComponentType.COMPONENT_AIRFRAME -> airframe_time
  ComponentType.COMPONENT_PROPELLER -> prop_time
  else -> engine_hour.takeIf { it > 0.0 } ?: airframe_time.takeIf { it > 0.0 }
  ?: prop_time
}

@Preview
@Composable
private fun PreviewMaintenanceLogCard() {
  MaintenanceLogCard(
    log = MaintenanceLog(
      id = "preview-1",
      timestamp = Instant.fromEpochSeconds(1_745_000_000)
        .toWireInstant(),
      work_description = "Replaced left magneto per SB-1234. Performed mag drop check — within limits.",
      component_type = ComponentType.COMPONENT_ENGINE,
      engine_hour = 1432.5,
      inspection_ids = listOf(
        "insp-1",
        "insp-2"
      ),
      technician = Technician(
        id = "tech-1",
        name = "J. Rivera"
      ),
    ),
    onClick = {},
  )
}
