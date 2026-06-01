package dev.fanfly.wingslog.feature.fleet.viewing

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.ic_launcher_foreground
import wingslog.feature.fleet.sharedassets.generated.resources.add_first_aircraft
import wingslog.feature.fleet.sharedassets.generated.resources.no_fleet_description
import wingslog.feature.fleet.sharedassets.generated.resources.no_fleet_title
import wingslog.core.sharedassets.generated.resources.Res as UiRes
import wingslog.feature.fleet.sharedassets.generated.resources.Res as FleetRes

@Composable
fun FleetEmptyState(
  onAddAircraft: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bobTransition = rememberInfiniteTransition(label = "emptyFleetBob")
  val bobY by bobTransition.animateFloat(
    initialValue = 0f,
    targetValue = -6f,
    animationSpec = infiniteRepeatable(
      animation = tween(1700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "emptyFleetBobY",
  )
  val bobRotation by bobTransition.animateFloat(
    initialValue = -1.5f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "emptyFleetBobRotation",
  )

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    EmptyState(
      title = stringResource(FleetRes.string.no_fleet_title),
      description = stringResource(FleetRes.string.no_fleet_description),
      iconContent = {
        Icon(
          painter = painterResource(UiRes.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = Modifier
            .size(112.dp)
            .offset(y = bobY.dp)
            .rotate(bobRotation),
          tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
        )
      },
      actionText = stringResource(FleetRes.string.add_first_aircraft),
      onActionClick = onAddAircraft,
    )
  }
}
