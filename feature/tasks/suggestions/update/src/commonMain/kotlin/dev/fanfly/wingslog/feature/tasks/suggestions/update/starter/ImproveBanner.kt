package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_add_details
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_improve_body
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_improve_title
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_use_manual

/**
 * The model had nothing confident to say (PRD R21a; 1e): more about the Thing is what would help,
 * either its details or a manual. The common-practice rows below stay usable. Amber, the advisory
 * accent, only on the outline and the icon.
 */
@Composable
internal fun ImproveBanner(onAddDetails: () -> Unit, onUseManual: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.tertiary),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
          Icons.Outlined.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(Res.string.suggestions_improve_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(
              Res.string.suggestions_improve_body,
              LocalThingLexicon.current.thingNoun.singular,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        OutlinedButton(onClick = onAddDetails) {
          Text(stringResource(Res.string.starter_pack_add_details))
        }
        TextButton(onClick = onUseManual) {
          Icon(
            Icons.Outlined.UploadFile,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.suggestions_use_manual))
        }
      }
    }
  }
}
