package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun InspectionScheduleTab(
  isOneTime: Boolean,
  onOneTimeChange: (Boolean) -> Unit,
  intervalMonths: String,
  onMonthsChange: (String) -> Unit,
  intervalHours: String,
  onHoursChange: (String) -> Unit,
  linkedToId: String?,
  onLinkChange: (String?) -> Unit,
  availableInspections: List<InspectionCard>,
) {
  OneTimeComplianceFields(
    isOneTime = isOneTime,
    onOneTimeChange = onOneTimeChange
  )

  Spacer(modifier = Modifier.height(Spacing.large))

  if (linkedToId == null) {
    IntervalFields(
      intervalMonths = intervalMonths,
      onMonthsChange = onMonthsChange,
      intervalHours = intervalHours,
      onHoursChange = onHoursChange
    )
  }

  Spacer(modifier = Modifier.height(Spacing.large))

  LinkedInspectionFields(
    linkedToId = linkedToId,
    onLinkChange = onLinkChange,
    availableInspections = availableInspections
  )
}
