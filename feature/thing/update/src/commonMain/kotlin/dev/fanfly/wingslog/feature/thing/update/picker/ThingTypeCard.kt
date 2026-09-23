package dev.fanfly.wingslog.feature.thing.update.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.ThingTemplate

@Composable
internal fun ThingTypeCard(
  template: ThingTemplate,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(Spacing.large),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        thingIcon(template.icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        // display_name, not the lexicon noun: the picker names the type, and the lexicon is the
        // vocabulary a Thing gets *after* it has one.
        template.display_name,
        style = MaterialTheme.typography.titleSmall,
      )
    }
  }
}
