package dev.fanfly.wingslog.feature.maintenance.sharedassets.util

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.component_airframe
import wingslog.core.ui.generated.resources.component_engine
import wingslog.core.ui.generated.resources.component_propeller
import wingslog.core.ui.generated.resources.unknown
import wingslog.core.ui.generated.resources.Res as CoreRes

@Composable
fun MaintenanceLog.ComponentType.displayName(): String = when (this) {
  MaintenanceLog.ComponentType.AIRFRAME -> stringResource(CoreRes.string.component_airframe)
  MaintenanceLog.ComponentType.ENGINE -> stringResource(CoreRes.string.component_engine)
  MaintenanceLog.ComponentType.PROPELLER -> stringResource(CoreRes.string.component_propeller)
  else -> stringResource(CoreRes.string.unknown)
}
