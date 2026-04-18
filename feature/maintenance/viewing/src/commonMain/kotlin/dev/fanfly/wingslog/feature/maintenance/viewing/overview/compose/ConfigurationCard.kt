package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.component_airframe
import wingslog.core.ui.generated.resources.component_engine
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.maintenance.sharedassets.generated.resources.engine_with_index
import wingslog.feature.maintenance.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.viewing.generated.resources.aircraft_data
import wingslog.feature.maintenance.viewing.generated.resources.collapse_details
import wingslog.feature.maintenance.viewing.generated.resources.expand_details
import wingslog.feature.maintenance.viewing.generated.resources.s_n_placeholder


@Composable
fun ConfigurationCard(
  aircraft: Aircraft,
) {
  var expanded by rememberSaveable { mutableStateOf(true) }
  val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.chipCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
  ) {
    Column {
      // Accordion Header
      Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
        .padding(horizontal = Spacing.large, vertical = Spacing.large),
          verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Dataset,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
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
          modifier = Modifier.size(24.dp).rotate(rotationState),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Collapsible Content
      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier.padding(
            bottom = Spacing.large, start = Spacing.large, end = Spacing.large
          ), verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
          // Airframe Component Card
          ComponentCard(
            category = stringResource(CoreRes.string.component_airframe).uppercase(),
            name = "${aircraft.make} ${aircraft.model}",
            serial = aircraft.serial
          )

          // Engine(s)
          aircraft.engine.forEachIndexed { index, engine ->
            val label = if (aircraft.engine.size > 1) {
              stringResource(SharedRes.string.engine_with_index, index + 1)
            } else {
              stringResource(CoreRes.string.component_engine)
            }.uppercase()
            EngineDetails(label = label, engine = engine)
          }
        }
      }
    }
  }
}

@Composable
fun ComponentCard(
  category: String, name: String, serial: String, content: @Composable (() -> Unit)? = null,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.small),
    color = MaterialTheme.colorScheme.surfaceContainerLow // Theme-aware inset BG
  ) {
    Column(modifier = Modifier.padding(Spacing.large)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = category, style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              letterSpacing = 0.1.sp
            ), color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = name, modifier = Modifier.padding(top = 4.dp), style = TextStyle(
              fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
            ), color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(MaintenanceRes.string.s_n_placeholder, serial),
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp
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