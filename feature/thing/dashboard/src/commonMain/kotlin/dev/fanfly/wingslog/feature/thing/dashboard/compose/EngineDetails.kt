package dev.fanfly.wingslog.feature.thing.dashboard.compose

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
import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.childInSlot
import dev.fanfly.wingslog.core.template.childrenInSlot
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.component_propeller
import wingslog.core.sharedassets.generated.resources.make_model_template
import wingslog.feature.logs.viewing.generated.resources.s_n_placeholder
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes


@Composable
fun EngineDetails(
  label: String,
  engine: Component,
) {
  // Reads the inflated component tree rather than the transitional `Thing.engine` field (#668).
  // Same shape either way — ThingInflater builds airframe -> engine -> propeller -> hub/blade, and
  // TemplateKeysResolveTest asserts those slot keys against the airplane template — so this is a
  // change of source, not of layout. Rendering a tree whose shape comes from the *template* is
  // separate work (#729).
  ComponentCard(
    category = label,
    name = stringResource(
      CoreRes.string.make_model_template,
      engine.make,
      engine.model,
    ),
    serial = engine.serial,
    content = {
      val propeller = engine.childInSlot(SlotKeys.PROPELLER)
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
            text = stringResource(
              CoreRes.string.make_model_template,
              propeller.childInSlot(SlotKeys.HUB)?.make.orEmpty(),
              propeller.childInSlot(SlotKeys.HUB)?.model.orEmpty(),
            ),
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
              propeller.childInSlot(SlotKeys.HUB)?.serial.orEmpty()
            ),
            modifier = Modifier.padding(top = Spacing.extraSmall),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Normal,
              fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          val blades = propeller.childrenInSlot(SlotKeys.BLADE)
          if (blades.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = Spacing.large)) {
              BladeChipsOverview(blades)
            }
          }
        }
      }
    }
  )
}
