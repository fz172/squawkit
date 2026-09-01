package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.Component
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.s_n_empty
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes


/**
 * A repeatable leaf slot, as chips rather than a card each (#729).
 *
 * Was `BladeChipsOverview`, which knew about blades. The rule generalises without loss: a slot that
 * repeats and has no children of its own is a set of near-identical parts told apart by serial —
 * blades, tyres, wheels, batteries — and a full card each buries the tree in scroll for no gain.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComponentChips(label: String, components: List<Component>) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.small)
  ) {
    components.forEachIndexed { index, component ->
      Surface(
        shape = RoundedCornerShape(Spacing.badgeCornerRadius),
        color = Color.Transparent,
        border = BorderStroke(
          Spacing.hairline,
          MaterialTheme.colorScheme.outlineVariant
        ),
      ) {
        Column(
          modifier = Modifier.padding(
            horizontal = Spacing.medium,
            vertical = Spacing.small
          ),
        ) {
          Text(
            text = "$label ${index + 1}",
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
          Text(
            text = component.serial.ifBlank { stringResource(MaintenanceRes.string.s_n_empty) },
            modifier = Modifier.padding(top = Spacing.extraSmall),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}
