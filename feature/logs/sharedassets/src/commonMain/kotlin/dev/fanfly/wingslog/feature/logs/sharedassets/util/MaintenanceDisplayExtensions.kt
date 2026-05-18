package dev.fanfly.wingslog.feature.logs.sharedassets.util

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.aircraft.ComponentType
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.component_airframe
import wingslog.core.ui.generated.resources.component_engine
import wingslog.core.ui.generated.resources.component_propeller
import wingslog.core.ui.generated.resources.unknown

@Composable
fun ComponentType.displayName(): String = when (this) {
  ComponentType.COMPONENT_AIRFRAME -> stringResource(CoreRes.string.component_airframe)
  ComponentType.COMPONENT_ENGINE -> stringResource(CoreRes.string.component_engine)
  ComponentType.COMPONENT_PROPELLER -> stringResource(CoreRes.string.component_propeller)
  else -> stringResource(CoreRes.string.unknown)
}
