package dev.fanfly.wingslog.feature.technician.manage.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * A labelled value inside the Details card. With [onEdit] the row opens an editor and carries a
 * pencil; without it the value is locked (a lock glyph and a [supporting] line saying who owns it).
 */
@Composable
internal fun ProfileFieldRow(
  label: String,
  value: String,
  onEdit: (() -> Unit)? = null,
  placeholder: String? = null,
  supporting: String? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
      .padding(horizontal = Spacing.xLarge, vertical = Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val empty = value.isBlank() && placeholder != null
      Text(
        text = if (empty) placeholder.orEmpty() else value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
      )
      if (supporting != null) {
        Text(
          text = supporting,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.width(Spacing.large))
    Icon(
      imageVector = if (onEdit != null) Icons.Default.Edit else Icons.Default.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = Spacing.extraSmall),
    )
  }
}
