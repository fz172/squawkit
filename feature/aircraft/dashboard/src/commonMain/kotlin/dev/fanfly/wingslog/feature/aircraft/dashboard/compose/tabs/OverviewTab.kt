package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.AircraftDataCard
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.LogOnboardingCard
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.LogStatsSection
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.viewing.AogAlertSection
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.viewing.CriticalAlertsSection
import dev.fanfly.wingslog.feature.tasks.viewing.TaskCardItem
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.make_model_template
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  onViewSquawksTab: () -> Unit = {},
  onViewLogsTab: () -> Unit = {},
  onMutationAction: ((AircraftOverviewAction) -> Unit)? = onAction,
  modifier: Modifier = Modifier,
) {
  val tier = LocalLayoutTier.current
  if (tier == LayoutTier.LARGE) {
    LargeOverviewTab(
      state = state,
      onAction = onAction,
      onViewSquawksTab = onViewSquawksTab,
      onViewLogsTab = onViewLogsTab,
      onMutationAction = onMutationAction,
      modifier = modifier,
    )
    return
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
  ) {
    val overdueTasks =
      state.activeTasks.filter { it.dueStatus.status == DueStatus.OVERDUE || it.dueStatus.status == DueStatus.DUE_SOON }

    OverviewHero(state, Modifier.padding(horizontal = Spacing.screenPadding).padding(top = Spacing.medium))

    Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
      AircraftDataCard(
        state.aircraft,
        initiallyExpanded = overdueTasks.isEmpty()
      )
    }

    if (state.aogSquawks.isNotEmpty()) {
      AogAlertSection(
        aogSquawks = state.aogSquawks,
        onViewSquawksClick = onViewSquawksTab,
        modifier = Modifier.padding(horizontal = Spacing.screenPadding),
      )
    }

    if (overdueTasks.isNotEmpty()) {
      CriticalAlertsSection(
        overdueTasks = overdueTasks,
        onCardClick = { onAction(AircraftOverviewAction.TaskCardClick(it)) },
        modifier = Modifier.padding(horizontal = Spacing.screenPadding)
      )
    }

    state.logStats?.let { stats ->
      if (stats.total == 0L && onMutationAction != null) {
        LogOnboardingCard(
          onAddLogClick = { onMutationAction(AircraftOverviewAction.AddLogClick(state.aircraft.id)) },
          modifier = Modifier.padding(horizontal = Spacing.screenPadding)
        )
      } else if (stats.total > 0L) {
        LogStatsSection(
          stats = stats,
          modifier = Modifier.padding(horizontal = Spacing.screenPadding)
        )
      }
    }

    Spacer(Modifier.height(Spacing.screenPadding))
  }
}

@Composable
private fun LargeOverviewTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  onViewSquawksTab: () -> Unit,
  onViewLogsTab: () -> Unit,
  onMutationAction: ((AircraftOverviewAction) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val overdueTasks =
    state.activeTasks.filter { it.dueStatus.status == DueStatus.OVERDUE || it.dueStatus.status == DueStatus.DUE_SOON }
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.screenPadding)
      .padding(top = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    OverviewHero(state)

    AircraftDataCard(
      state.aircraft,
      initiallyExpanded = overdueTasks.isEmpty()
    )

    if (state.aogSquawks.isNotEmpty()) {
      AogAlertSection(
        aogSquawks = state.aogSquawks,
        onViewSquawksClick = onViewSquawksTab,
      )
    }

    if (overdueTasks.isNotEmpty()) {
      CriticalAlertsSection(
        overdueTasks = overdueTasks,
        onCardClick = { onAction(AircraftOverviewAction.TaskCardClick(it)) },
      )
    }

    state.logStats?.let { stats ->
      if (stats.total == 0L && onMutationAction != null) {
        LogOnboardingCard(
          onAddLogClick = { onMutationAction(AircraftOverviewAction.AddLogClick(state.aircraft.id)) },
        )
      } else if (stats.total > 0L) {
        LogStatsSection(stats = stats)
      }
    }

    DashboardLowerGrid(
      state = state,
      onTaskClick = { onAction(AircraftOverviewAction.TaskCardClick(it)) },
      onLogsClick = onViewLogsTab,
      onViewSquawksClick = onViewSquawksTab,
    )

    Spacer(Modifier.height(Spacing.screenPadding))
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewHero(
  state: AircraftOverviewUiState.Success,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      text = stringResource(
        CoreRes.string.make_model_template,
        state.aircraft.make.trim(),
        state.aircraft.model.trim()
      ),
      style = WingslogTypography.heroDisplay,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = state.aircraft.tail_number,
      style = WingslogTypography.heroDisplay,
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@Composable
private fun DashboardLowerGrid(
  state: AircraftOverviewUiState.Success,
  onTaskClick: (MaintenanceTaskWithStatus) -> Unit,
  onLogsClick: () -> Unit,
  onViewSquawksClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val nextTask = state.activeTasks.firstOrNull()
  val openSquawks = state.squawks.filter { it.status == SquawkStatus.OPEN }
    .sortedByDescending { it.squawk.created_at?.getEpochSecond() ?: 0L }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    RailCard(
      title = "Recent activity",
      actionLabel = "All logs",
      onActionClick = onLogsClick,
      modifier = Modifier.weight(1f),
    ) {
      if (state.recentLogs.isEmpty()) {
        EmptyRailState(
          icon = Icons.Default.Description,
          title = "No logs yet",
          body = "Add the first maintenance entry to start the aircraft record.",
        )
      } else {
        state.recentLogs.forEachIndexed { index, log ->
          RecentLogRow(log = log, onClick = onLogsClick)
          if (index < state.recentLogs.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
          }
        }
      }
    }

    Column(
      modifier = Modifier.width(320.dp),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      if (nextTask != null) {
        RailCard(title = "Next due") {
          TaskCardItem(
            cardWithStatus = nextTask,
            onClick = { onTaskClick(nextTask) },
          )
        }
      } else {
        RailCard(title = "Next due") {
          EmptyRailState(
            icon = Icons.Default.CheckCircle,
            title = "No upcoming tasks",
            body = "Scheduled maintenance is up to date.",
          )
        }
      }

      RailCard(title = "Open squawks", actionLabel = "All", onActionClick = onViewSquawksClick) {
        if (openSquawks.isEmpty()) {
          EmptyRailState(
            icon = Icons.Default.CheckCircle,
            title = "No open squawks",
            body = "No active discrepancies for this aircraft.",
          )
        } else {
          val previewSquawks = openSquawks.take(3)
          previewSquawks.forEachIndexed { index, item ->
            SquawkRailRow(
              title = item.squawk.title,
              subtitle = item.squawk.description.ifBlank {
                item.squawk.created_at?.toLocalDate()?.toDisplayFormat()
                  ?: ""
              },
              onClick = onViewSquawksClick,
            )
            if (index < previewSquawks.lastIndex) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RailCard(
  title: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onActionClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onActionClick != null) {
          Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onActionClick).padding(Spacing.extraSmall),
          )
        }
      }
      content()
    }
  }
}

@Composable
private fun RecentLogRow(log: MaintenanceLog, onClick: () -> Unit) {
  val date = log.timestamp?.toLocalDate()?.toDisplayFormat()
    ?: stringResource(TasksRes.string.unknown_date)
  val hours = when (log.component_type) {
    ComponentType.COMPONENT_ENGINE -> log.engine_hour
    ComponentType.COMPONENT_AIRFRAME -> log.airframe_time
    ComponentType.COMPONENT_PROPELLER -> log.prop_time
    else -> log.engine_hour.takeIf { it > 0.0 } ?: log.airframe_time.takeIf { it > 0.0 } ?: log.prop_time
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 64.dp)
      .clickable(onClick = onClick)
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    RailComponentTypeBadge(log.component_type)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = log.component_type.displayName(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = log.work_description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(date, style = WingslogTypography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (hours > 0.0) {
        Text(
          "${hours.formatToOneDecimalPlace()} hrs",
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
      }
    }
  }
}

@Composable
private fun SquawkRailRow(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = Icons.Default.Warning,
      contentDescription = null,
      tint = MaterialTheme.statusColors.critical.accent,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun RailComponentTypeBadge(type: ComponentType) {
  val (background, content) = when (type) {
    ComponentType.COMPONENT_ENGINE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    ComponentType.COMPONENT_AIRFRAME -> MaterialTheme.statusColors.positive.container to MaterialTheme.statusColors.positive.onContainer
    ComponentType.COMPONENT_PROPELLER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
  }
  Surface(
    shape = RoundedCornerShape(Spacing.badgeCornerRadius),
    color = background,
  ) {
    Text(
      text = type.displayName().uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = content,
      modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
    )
  }
}

@Composable
private fun EmptyRailState(
  icon: ImageVector,
  title: String,
  body: String,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}
