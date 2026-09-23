package dev.fanfly.wingslog.feature.settings.section

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.feature.settings.row.SettingsRow
import dev.fanfly.wingslog.feature.settings.row.SettingsRowGroup
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import wingslog.feature.settings.generated.resources.settings_export_subtitle
import wingslog.feature.settings.generated.resources.settings_section_data
import wingslog.feature.settings.generated.resources.settings_technicians_subtitle
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.feature.export.sharedassets.generated.resources.Res as ExportRes
import wingslog.feature.settings.generated.resources.Res as SettingsRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

/** The technician roster and logbook export. */
@Composable
internal fun DataSection(
  onOpenTechnicians: () -> Unit,
  onOpenExport: () -> Unit,
) {
  GroupedSection(stringResource(SettingsRes.string.settings_section_data)) {
    SettingsRowGroup(
      listOf(
        {
          SettingsRow(
            icon = Icons.Default.Engineering,
            // Fixed text, not a lexicon substitution. Settings should read the same whatever
            // the picker holds, and no lexicon noun is right here anyway: the generic word is
            // "person", so this row would say "Person Profiles". The domain-specific framing
            // lives inside the screen this opens (manage_technicians_description).
            title = stringResource(TechnicianRes.string.manage_technicians),
            subtitle = stringResource(SettingsRes.string.settings_technicians_subtitle),
            onClick = onOpenTechnicians,
          )
        },
        {
          SettingsRow(
            icon = Icons.Default.FileDownload,
            title = stringResource(ExportRes.string.feature_name_export_logs),
            subtitle = stringResource(SettingsRes.string.settings_export_subtitle),
            onClick = onOpenExport,
          )
        },
      )
    )
  }
}
