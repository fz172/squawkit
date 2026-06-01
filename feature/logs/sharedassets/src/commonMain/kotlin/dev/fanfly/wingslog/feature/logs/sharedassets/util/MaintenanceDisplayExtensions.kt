package dev.fanfly.wingslog.feature.logs.sharedassets.util

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.aircraft.ComponentType
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.component_airframe
import wingslog.core.sharedassets.generated.resources.component_engine
import wingslog.core.sharedassets.generated.resources.component_propeller
import wingslog.core.sharedassets.generated.resources.unknown

@Composable
fun ComponentType.displayName(): String = when (this) {
  ComponentType.COMPONENT_AIRFRAME -> stringResource(Res.string.component_airframe)
  ComponentType.COMPONENT_ENGINE -> stringResource(Res.string.component_engine)
  ComponentType.COMPONENT_PROPELLER -> stringResource(Res.string.component_propeller)
  else -> stringResource(Res.string.unknown)
}
