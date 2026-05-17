package dev.fanfly.wingslog.feature.aircraft.dashboard.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.component_propeller
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.logs.viewing.generated.resources.s_n_placeholder


@Composable
fun EngineDetails(
  label: String,
  engine: Engine,
) {
  ComponentCard(
    category = label,
    name = "${engine.make} ${engine.model}",
    serial = engine.serial,
    content = {
      val propeller = engine.propeller
      if (propeller != null) {
        Column {
          Text(
            text = stringResource(CoreRes.string.component_propeller).uppercase(),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.primary
          )

          Text(
            text = "${propeller.hub?.make} ${propeller.hub?.model}",
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
              propeller.hub?.serial ?: ""
            ),
            modifier = Modifier.padding(top = Spacing.extraSmall),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Normal,
              fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          if (propeller.blades.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = Spacing.large)) {
              BladeChipsOverview(propeller.blades)
            }
          }
        }
      }
    }
  )
}
