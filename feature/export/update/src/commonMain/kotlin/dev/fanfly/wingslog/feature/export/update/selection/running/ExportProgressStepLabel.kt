package dev.fanfly.wingslog.feature.export.update.selection.running

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_progress_building_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_collecting_data
import wingslog.feature.export.sharedassets.generated.resources.export_progress_compressing_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_saving_file
import wingslog.feature.export.sharedassets.generated.resources.export_progress_uploading_archive

@Composable
internal fun ExportProgressStep.label(): String = when (this) {
  // Neutral: one export can cover a car and a house at once, so "Collecting vehicle data" would
  // be wrong for half of it.
  ExportProgressStep.COLLECTING_DATA ->
    stringResource(Res.string.export_progress_collecting_data)

  ExportProgressStep.BUILDING_ARCHIVE -> stringResource(Res.string.export_progress_building_archive)
  ExportProgressStep.COMPRESSING_ARCHIVE -> stringResource(Res.string.export_progress_compressing_archive)
  ExportProgressStep.SAVING_FILE -> stringResource(Res.string.export_progress_saving_file)
  ExportProgressStep.UPLOADING_ARCHIVE -> stringResource(Res.string.export_progress_uploading_archive)
}
