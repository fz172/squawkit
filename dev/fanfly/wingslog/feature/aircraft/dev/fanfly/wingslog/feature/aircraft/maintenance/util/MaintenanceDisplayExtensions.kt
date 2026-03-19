package dev.fanfly.wingslog.feature.aircraft.maintenance.util

import dev.fanfly.wingslog.aircraft.MaintenanceLog

fun MaintenanceLog.ComponentType.displayName(): String = when (this) {
  MaintenanceLog.ComponentType.AIRFRAME -> "Airframe"
  MaintenanceLog.ComponentType.ENGINE -> "Engine"
  MaintenanceLog.ComponentType.PROPELLER -> "Propeller"
  else -> "Unknown"
}
