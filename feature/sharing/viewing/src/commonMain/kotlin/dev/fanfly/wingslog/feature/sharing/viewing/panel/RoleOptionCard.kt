package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_co_owner_desc
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_technician_desc

@Composable
internal fun RoleOptionCard(
  role: ShareRole,
  selected: Boolean,
  onClick: () -> Unit
) {
  val borderColor =
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
  val backgroundColor = if (selected) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }
  val (name, desc, icon) = when (role) {
    ShareRole.TECHNICIAN -> Triple(
      LexiconFormatter.titleCase(LocalThingLexicon.current.technicianNoun),
      stringResource(
        Res.string.manage_access_role_technician_desc,
        LocalThingLexicon.current.squawkNoun.plural,
        LocalThingLexicon.current.taskNoun.plural,
        LocalThingLexicon.current.logNoun.plural,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      Icons.Filled.Construction,
    )

    ShareRole.OWNER -> Triple(
      stringResource(Res.string.invite_role_owner),
      stringResource(
        Res.string.manage_access_role_co_owner_desc,
        LocalThingLexicon.current.technicianNoun.singular,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      Icons.Filled.Flight,
    )
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .background(backgroundColor, RoundedCornerShape(Spacing.cardCornerRadius))
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .padding(Spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
      Text(name, style = MaterialTheme.typography.titleSmall)
      Text(
        desc,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
