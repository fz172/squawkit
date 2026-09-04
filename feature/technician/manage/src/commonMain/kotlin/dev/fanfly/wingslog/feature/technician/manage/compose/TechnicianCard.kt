package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.template.derivedRoles
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.certificationLines
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.summary
import dev.fanfly.wingslog.thing.Technician
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.linked_badge
import wingslog.feature.technician.sharedassets.generated.resources.no_certifications
import wingslog.feature.technician.sharedassets.generated.resources.you_badge

@Composable
fun TechnicianCard(
  technician: Technician,
  /** What the account's templates declare — the words a stored certification key renders under. */
  offered: List<OfferedCertification>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isSelf: Boolean = false,
  /** A profile mirrored from a share member: shown read-only, badged, and not editable (§7.3). */
  isLinked: Boolean = false,
) {
  val certifications = technician.certificationLines(offered)
  // Derived, never stored (PRD §8.6). Empty for someone uncertified, and for a credential whose
  // template this build does not carry — untagged rather than an error, in both cases.
  val roles = technician.derivedRoles(offered)

  Card(
    onClick = onClick,
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = technician.name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        val badge = when {
          isSelf -> stringResource(Res.string.you_badge)
          isLinked -> stringResource(Res.string.linked_badge)
          else -> null
        }
        if (badge != null) {
          SuggestionChip(
            onClick = {},
            label = {
              Text(badge, style = MaterialTheme.typography.labelSmall)
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
              // Linked profiles are someone else's data — a quieter tone than the primary "You".
              containerColor = if (isLinked) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.primaryContainer,
              labelColor = if (isLinked) MaterialTheme.colorScheme.onSecondaryContainer
              else MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      if (roles.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
          roles.forEach { role ->
            SuggestionChip(
              onClick = {},
              label = { Text(role, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }
      }

      if (certifications.isEmpty()) {
        Text(
          text = stringResource(Res.string.no_certifications),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
        )
      } else {
        certifications.forEach { line ->
          Text(
            text = line.summary(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          val expiration = line.expiration
          if (expiration != null) {
            Text(
              text = expiration,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
