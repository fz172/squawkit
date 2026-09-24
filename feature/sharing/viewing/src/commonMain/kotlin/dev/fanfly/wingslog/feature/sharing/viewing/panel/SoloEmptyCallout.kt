package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_solo_body
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_solo_title

@Composable
internal fun SoloEmptyCallout() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        MaterialTheme.colorScheme.primaryContainer,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .padding(Spacing.large),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      Icons.Filled.GroupAdd,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Column {
      Text(
        stringResource(Res.string.manage_access_solo_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
        stringResource(
          Res.string.manage_access_solo_body,
          LocalThingLexicon.current.squawkNoun.plural,
          LocalThingLexicon.current.thingNoun.singular,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}
