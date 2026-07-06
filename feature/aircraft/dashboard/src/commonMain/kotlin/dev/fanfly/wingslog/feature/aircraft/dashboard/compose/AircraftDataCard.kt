package dev.fanfly.wingslog.feature.aircraft.dashboard.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.component_airframe
import wingslog.core.sharedassets.generated.resources.component_engine
import wingslog.core.sharedassets.generated.resources.edit
import wingslog.core.sharedassets.generated.resources.make_model_template
import wingslog.feature.logs.sharedassets.generated.resources.engine_with_index
import wingslog.feature.logs.viewing.generated.resources.aircraft_data
import wingslog.feature.logs.viewing.generated.resources.collapse_details
import wingslog.feature.logs.viewing.generated.resources.expand_details
import wingslog.feature.logs.viewing.generated.resources.s_n_placeholder
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes


@Composable
fun AircraftDataCard(
  aircraft: Aircraft,
  initiallyExpanded: Boolean = true,
  onEditClick: (() -> Unit)? = null,
) {
  var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
  val rotationState by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
  )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    )
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth()
          .clickable { expanded = !expanded }
          .padding(
            horizontal = Spacing.large,
            vertical = Spacing.large
          ),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Dataset,
          contentDescription = null,
          modifier = Modifier.size(Spacing.xLarge),
          tint = MaterialTheme.colorScheme.primary
        )

        Text(
          text = stringResource(MaintenanceRes.string.aircraft_data),
          modifier = Modifier
            .padding(start = Spacing.medium)
            .weight(1f),

          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            letterSpacing = 0.05.sp
          ),
          color = MaterialTheme.colorScheme.secondary
        )

        Icon(
          imageVector = Icons.Default.KeyboardArrowDown,
          contentDescription = if (expanded) stringResource(MaintenanceRes.string.collapse_details) else stringResource(
            MaintenanceRes.string.expand_details
          ),
          modifier = Modifier.size(Spacing.extraLarge)
            .rotate(rotationState),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier.padding(
            bottom = Spacing.large,
            start = Spacing.large,
            end = Spacing.large
          ),
          verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
          ComponentCard(
            category = stringResource(CoreRes.string.component_airframe).uppercase(),
            name = stringResource(
              CoreRes.string.make_model_template,
              aircraft.make,
              aircraft.model,
            ),
            serial = aircraft.serial
          )

          aircraft.engine.forEachIndexed { index, engine ->
            val label = if (aircraft.engine.size > 1) {
              stringResource(
                SharedRes.string.engine_with_index,
                index + 1
              )
            } else {
              stringResource(CoreRes.string.component_engine)
            }.uppercase()
            EngineDetails(
              label = label,
              engine = engine
            )
          }

          if (onEditClick != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              TextButton(onClick = onEditClick) {
                Text(text = stringResource(CoreRes.string.edit))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ComponentCard(
  category: String,
  name: String,
  serial: String,
  content: @Composable (() -> Unit)? = null,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = Color.Transparent,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    )
  ) {
    Column(modifier = Modifier.padding(Spacing.large)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = category,
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = name,
            modifier = Modifier.padding(top = Spacing.extraSmall),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.SemiBold,
              fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(
              MaintenanceRes.string.s_n_placeholder,
              serial
            ),
            modifier = Modifier.padding(top = Spacing.extraSmall),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Normal,
              fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (content != null) {
        Column(modifier = Modifier.padding(top = Spacing.large)) {
          content()
        }
      }
    }
  }
}
