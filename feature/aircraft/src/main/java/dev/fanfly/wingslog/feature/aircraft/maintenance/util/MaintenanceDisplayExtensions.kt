package dev.fanfly.wingslog.feature.aircraft.maintenance.util

import dev.fanfly.wingslog.aircraft.MaintenanceLog

fun MaintenanceLog.InspectionType.displayName(): String = when (this) {
    MaintenanceLog.InspectionType.ANNUAL -> "Annual"
    MaintenanceLog.InspectionType.HUNDRED_HOUR -> "100 Hour"
    MaintenanceLog.InspectionType.ROUTINE -> "Routine"
    MaintenanceLog.InspectionType.TRANSPONDER_CHECK -> "Transponder Check"
    MaintenanceLog.InspectionType.CONDITIONAL -> "Conditional"
    MaintenanceLog.InspectionType.OIL_CHANGE -> "Oil Change"
    MaintenanceLog.InspectionType.ELT -> "ELT"
    MaintenanceLog.InspectionType.ALTIMETER_PITOT_STATIC -> "Altimeter/Pitot-Static"
    else -> name
}

fun MaintenanceLog.ComponentType.displayName(): String = when (this) {
    MaintenanceLog.ComponentType.AIRFRAME -> "Airframe"
    MaintenanceLog.ComponentType.ENGINE -> "Engine"
    MaintenanceLog.ComponentType.PROPELLER -> "Propeller"
    else -> "Unknown"
}
