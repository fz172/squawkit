package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable

// "1.0.260828(1400)" — the same shape iOS renders from MARKETING_VERSION and
// CURRENT_PROJECT_VERSION, so the three platforms are comparable at a glance (#672).
@Composable
actual fun getAppVersion(): String =
  "$GENERATED_VERSION_NAME($GENERATED_VERSION_CODE)"
