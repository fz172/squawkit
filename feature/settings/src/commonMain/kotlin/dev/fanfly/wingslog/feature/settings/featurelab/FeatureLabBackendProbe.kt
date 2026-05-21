package dev.fanfly.wingslog.feature.settings.featurelab

expect class FeatureLabBackendProbe() {
  suspend fun callHealthProbe(): String
}
