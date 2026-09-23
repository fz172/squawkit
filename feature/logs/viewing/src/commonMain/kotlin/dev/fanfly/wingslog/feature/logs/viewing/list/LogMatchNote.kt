package dev.fanfly.wingslog.feature.logs.viewing.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import dev.fanfly.wingslog.feature.search.datamanager.LogAdapter
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.viewing.hiddenMatchNote
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.match_serial
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

/** The serial is searched but not shown on a log card; say so when it is the only match. */
@Composable
internal fun logMatchNote(
  matches: List<FieldMatch>,
  log: MaintenanceLog
): AnnotatedString? =
  hiddenMatchNote(
    matches,
    setOf(
      LogAdapter.FIELD_DESCRIPTION,
      LogAdapter.FIELD_TECHNICIAN
    )
  ) { match ->
    if (match.field == LogAdapter.FIELD_SERIAL) stringResource(
      SearchRes.string.match_serial,
      log.component_serial
    ) else null
  }
