package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.maintenance.sharedassets.generated.resources.blade_with_index
import wingslog.feature.maintenance.viewing.generated.resources.s_n_empty
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.maintenance.viewing.generated.resources.Res as MaintenanceRes


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BladeChipsOverview(blades: List<PropellerBlade>) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.small)
  ) {
    blades.forEachIndexed { index, blade ->
      Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(
            text = stringResource(SharedRes.string.blade_with_index, index + 1),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
          Text(
            text = blade.serial.ifBlank { stringResource(MaintenanceRes.string.s_n_empty) },
            modifier = Modifier.padding(top = 2.dp),
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
