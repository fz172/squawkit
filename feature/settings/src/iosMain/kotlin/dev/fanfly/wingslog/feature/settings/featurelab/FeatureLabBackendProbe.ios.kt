package dev.fanfly.wingslog.feature.settings.featurelab

actual class FeatureLabBackendProbe actual constructor() {
  actual suspend fun callHealthProbe(): String =
    "Live health_probe callable is currently wired only on Android."
}
