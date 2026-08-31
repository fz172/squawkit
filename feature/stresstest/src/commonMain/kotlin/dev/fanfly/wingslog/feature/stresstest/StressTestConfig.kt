package dev.fanfly.wingslog.feature.stresstest

data class StressTestConfig(
  val engineCount: Int = 1,
  val bladesPerEngine: Int = 2,
  val squawkCount: Int = 6,
  val taskCount: Int = 10,
  val logCount: Int = 36,
  val technicianCount: Int = 3,
  /**
   * Stamps the generated thing with DNA naming a `min_app_version` above this build, so it
   * resolves to the degraded state (#728, design §6.2).
   *
   * The only way to reach that state on a device: it needs a Thing written by a *newer* build, and
   * every template this build ships has a floor of 0. Generate once with this off and once with it
   * on to see a degraded thing sitting beside a healthy one in the switcher — which is where the
   * "never hidden" rule is actually visible.
   */
  val dnaFromANewerBuild: Boolean = false,
)
