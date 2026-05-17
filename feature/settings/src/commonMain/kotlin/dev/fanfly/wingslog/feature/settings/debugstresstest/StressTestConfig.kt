package dev.fanfly.wingslog.feature.settings.debugstresstest

data class StressTestConfig(
    val engineCount: Int = 1,
    val bladesPerEngine: Int = 2,
    val squawkCount: Int = 6,
    val taskCount: Int = 10,
    val logCount: Int = 36,
    val technicianCount: Int = 3,
)
