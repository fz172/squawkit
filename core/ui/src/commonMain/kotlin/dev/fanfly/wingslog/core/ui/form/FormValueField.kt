package dev.fanfly.wingslog.core.ui.form

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * A form value that is not typed into: a picker, a dropdown anchor, or a locked read-out.
 *
 * The two must not look alike. An [interactive] field is a bordered, 48dp control with a trailing
 * chevron; a read-out is plain text, and says why with [lockedReason] when the lock is by design.
 * A dropdown anchored with `menuAnchor` has no [onClick] of its own, so it passes [interactive].
 */
@Composable
fun FormValueField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  showLabel: Boolean = true,
  placeholder: String? = null,
  supportingText: String? = null,
  isError: Boolean = false,
  onClick: (() -> Unit)? = null,
  interactive: Boolean = onClick != null,
  lockedReason: String? = null,
  accessibilityDescription: String = label,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  valueStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  maxLines: Int = Int.MAX_VALUE,
) {
  val shape = RoundedCornerShape(Spacing.chipCornerRadius)
  val controlModifier = if (!interactive) {
    Modifier.padding(vertical = Spacing.extraSmall)
  } else {
    Modifier
      .heightIn(min = LocalMinimumInteractiveComponentSize.current)
      .clip(shape)
      .border(
        width = Spacing.hairline,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.outlineVariant,
        shape = shape,
      )
      .then(
        if (onClick == null) Modifier
        else Modifier
          .semantics(mergeDescendants = true) {
            contentDescription = accessibilityDescription
            role = Role.Button
          }
          .clickable(onClick = onClick)
      )
      .padding(horizontal = Spacing.large, vertical = Spacing.small)
  }
  val shownValue = value.ifEmpty { placeholder ?: "-" }
  val valueColor = when {
    isError -> MaterialTheme.colorScheme.error
    value.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.extraSmall),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    if (showLabel) {
      FormSectionLabel(
        text = label,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth()
        .then(controlModifier),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leadingIcon != null) {
        leadingIcon()
        Spacer(Modifier.width(Spacing.medium))
      }
      Text(
        text = shownValue,
        modifier = Modifier.weight(1f),
        style = valueStyle,
        color = valueColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
      )
      if (trailingIcon != null) {
        Spacer(Modifier.width(Spacing.medium))
        trailingIcon()
      } else if (interactive) {
        Spacer(Modifier.width(Spacing.medium))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (supportingText != null) {
      Text(
        text = supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (lockedReason != null && !interactive) FormLockedNote(lockedReason)
  }
}
