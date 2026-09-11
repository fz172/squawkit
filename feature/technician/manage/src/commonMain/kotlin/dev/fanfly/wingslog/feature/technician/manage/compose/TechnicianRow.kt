package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.ui.common.compose.GroupedChevron
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRow
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.certificationLines
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.summary
import dev.fanfly.wingslog.thing.Technician
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.linked_badge
import wingslog.feature.technician.sharedassets.generated.resources.no_certifications
import wingslog.feature.technician.sharedassets.generated.resources.you_badge

private val RowAvatarSize = 40.dp
private const val LinkedInfoAlpha = 0.6f

/**
 * One person on the roster: initials, name, and every credential on its own line. Your own record
 * carries a "You" stamp; a profile mirrored from someone's share carries "Linked" and opens an
 * explanation rather than an editor (§7.3).
 */
@Composable
fun TechnicianRow(
  technician: Technician,
  /** What the account's templates declare — the words a stored certification key renders under. */
  offered: List<OfferedCertification>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isSelf: Boolean = false,
  isLinked: Boolean = false,
) {
  val certifications = technician.certificationLines(offered)
  // One line per credential, expiry inline — "A&P Mechanic · A7584747 (Exp 08/31/2031)".
  val subtitle = if (certifications.isEmpty()) stringResource(Res.string.no_certifications)
  else certifications.map { it.summary() }.joinToString("\n")

  GroupedRow(
    title = technician.name,
    subtitle = subtitle,
    onClick = onClick,
    modifier = modifier,
    leading = {
      AvatarIcon(
        displayName = technician.name,
        photoUri = null,
        size = RowAvatarSize,
        textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      )
    },
    trailing = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        when {
          isSelf -> {
            YouStamp()
            Spacer(Modifier.width(Spacing.small))
          }

          isLinked -> {
            StatusChip(label = stringResource(Res.string.linked_badge), tier = StatusTier.NEUTRAL)
            Spacer(Modifier.width(Spacing.small))
          }
        }
        if (isLinked) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LinkedInfoAlpha),
          )
        } else {
          GroupedChevron()
        }
      }
    },
  )
}

/** The "You" stamp — the same shape as [StatusChip], in the primary tint no status uses. */
@Composable
private fun YouStamp() {
  Text(
    text = stringResource(Res.string.you_badge).uppercase(),
    style = MaterialTheme.typography.labelSmall.copy(
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    ),
    color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier = Modifier
      .background(
        MaterialTheme.colorScheme.primaryContainer,
        RoundedCornerShape(Spacing.badgeCornerRadius),
      )
      .padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
  )
}
