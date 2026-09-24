package dev.fanfly.wingslog.feature.technician.sharedassets.certification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.ui.form.DashedButton
import dev.fanfly.wingslog.core.ui.form.FormSectionLabel
import dev.fanfly.wingslog.core.ui.popup.DropdownMenu
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.CertExpireLimit
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.add_certification
import wingslog.feature.technician.sharedassets.generated.resources.certifications
import wingslog.feature.technician.sharedassets.generated.resources.custom_certification

/**
 * The certifications a person holds, added from what the account's templates declare (PRD §8.6).
 *
 * **No role question.** The credential says which domain the person works in, so the flow asks for
 * the credential and derives the rest. That is one question where the earlier draft had two, and it
 * is why a technician who is both an A&P and an ASE mechanic is one record with two rows rather
 * than two records the duplicate detector then has to reconcile.
 *
 * The section always renders, even when [offered] is empty: **Custom is always on the menu**, so an
 * account holding only Things whose templates declare no credentials can still record the licence
 * its handyman actually holds.
 */
@Composable
fun CertificationInputFields(
  entries: List<CertificationEntry>,
  offered: List<OfferedCertification>,
  onAdd: (String) -> Unit,
  onAddCustom: () -> Unit,
  onRemove: (Int) -> Unit,
  onNumberChanged: (Int, String) -> Unit,
  onLabelChanged: (Int, String) -> Unit,
  onExpireLimitChanged: (Int, CertExpireLimit) -> Unit,
  onExpirationChanged: (Int, Instant) -> Unit,
  modifier: Modifier = Modifier,
  /** False when the caller already labels the section — a grouped card with its own header. */
  showHeader: Boolean = true,
) {

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.columnGap),
  ) {
    if (showHeader) {
      FormSectionLabel(text = stringResource(Res.string.certifications))
    }

    entries.forEachIndexed { index, entry ->
      CertificationRow(
        entry = entry,
        offered = offered.firstOrNull { it.key == entry.type },
        onRemove = { onRemove(index) },
        onNumberChanged = { onNumberChanged(index, it) },
        onLabelChanged = { onLabelChanged(index, it) },
        onExpireLimitChanged = { onExpireLimitChanged(index, it) },
        onExpirationChanged = { onExpirationChanged(index, it) },
      )
    }

    // Only what the person does not already hold: a second A&P is not a thing anyone has. Custom
    // is always last and always available — the whole point of it is a credential no list predicted.
    val addable =
      offered.filterNot { candidate -> entries.any { it.type == candidate.key } }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
      DashedButton(
        label = stringResource(Res.string.add_certification),
        onClick = { menuOpen = true },
        modifier = Modifier.fillMaxWidth(),
      )
      DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false }) {
        addable.forEach { candidate ->
          DropdownMenuItem(
            text = { Text(candidate.label) },
            onClick = {
              menuOpen = false
              onAdd(candidate.key)
            },
          )
        }
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.custom_certification)) },
          onClick = {
            menuOpen = false
            onAddCustom()
          },
        )
      }
    }
  }
}
