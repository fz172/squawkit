package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.compose.PickerActionButton
import dev.fanfly.wingslog.core.ui.common.compose.PickerDoneButton
import dev.fanfly.wingslog.core.ui.common.compose.PickerSectionHeader
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectableRow
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectionMode
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.displayResId
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.resolvedCertificateType
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.done
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.linked_badge
import wingslog.feature.technician.sharedassets.generated.resources.linked_technicians_header
import wingslog.feature.technician.sharedassets.generated.resources.select_technician
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianPickerSheet(
  availableTechnicians: List<Technician>,
  selectedId: String?,
  onSelect: (Technician?) -> Unit,
  onAddClick: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  /**
   * Members of this aircraft's share who have published a mirror. Selecting one snapshots their
   * mirror fields into the log exactly as a local record would — only the source differs (§7.3).
   */
  linkedTechnicians: List<Technician> = emptyList(),
  /** The caller's own record, listed first. Null when they have no self-technician yet. */
  selfId: String? = null,
) =
  PickerSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    headerSlot = {
      Text(
        text = stringResource(TechnicianRes.string.select_technician),
        style = MaterialTheme.typography.titleLarge
      )
    }
  ) {
    // Self first, then the linked members, then the manual entries — the order of §7.3.
    val self = availableTechnicians.filter { it.id == selfId }
    val manual = availableTechnicians.filter { it.id != selfId }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      self.forEach { technician ->
        TechnicianRow(technician, selectedId, onSelect)
      }

      if (linkedTechnicians.isNotEmpty()) {
        PickerSectionHeader(stringResource(TechnicianRes.string.linked_technicians_header))
        val linkedBadge = stringResource(TechnicianRes.string.linked_badge)
        linkedTechnicians.forEach { technician ->
          TechnicianRow(technician, selectedId, onSelect, badge = linkedBadge)
        }
      }

      manual.forEach { technician ->
        TechnicianRow(technician, selectedId, onSelect)
      }

      PickerActionButton(
        text = stringResource(TechnicianRes.string.add_technician),
        icon = Icons.Default.Add,
        onClick = onAddClick,
      )

      PickerDoneButton(
        text = stringResource(CoreRes.string.done),
        onClick = onDismiss,
      )
    }
  }

@Composable
private fun TechnicianRow(
  technician: Technician,
  selectedId: String?,
  onSelect: (Technician?) -> Unit,
  badge: String? = null,
) {
  val certType = technician.resolvedCertificateType()
  val certText = listOfNotNull(
    if (certType == CertificateType.CERTIFICATE_TYPE_NONE) null
    else stringResource(certType.displayResId()),
    technician.cert_number.takeIf { it.isNotBlank() },
  ).joinToString(" · ")

  PickerSelectableRow(
    title = technician.name,
    subtitle = certText,
    selected = technician.id == selectedId,
    selectionMode = PickerSelectionMode.RADIO,
    onClick = { onSelect(technician) },
    badge = badge,
  )
}
