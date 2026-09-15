package dev.fanfly.wingslog.feature.datalog.viewing.analytics

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.DataLogImportFailed
import dev.fanfly.wingslog.core.analytics.DataLogImportFailureReason
import dev.fanfly.wingslog.core.analytics.DataLogImportSource
import dev.fanfly.wingslog.core.analytics.DataLogImported
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure

/**
 * The import half of design §13.1, shared by the two pickers that can start one so the list and the
 * attachment sheet cannot drift into reporting the same import differently.
 */
class DataLogImportTelemetry(
  private val analytics: AnalyticsManager,
  private val templates: CurrentThingTemplate,
  private val source: DataLogImportSource,
) {

  /**
   * [stored] is the parser's own answer for duration, series and recorder product; [file] is the
   * only place the byte size survives, because the stored record holds rows rather than the file.
   */
  fun imported(stored: DataLog, file: PickedFile) {
    analytics.log(
      DataLogImported(
        templateId = templates.templateId,
        source = source,
        format = stored.source?.product.orEmpty(),
        durationSeconds = stored.duration_seconds,
        sizeBytes = file.sizeBytes,
        seriesCount = stored.series.size,
      )
    )
  }

  fun failed(reason: ImportFailure, file: PickedFile) {
    analytics.log(
      DataLogImportFailed(
        templateId = templates.templateId,
        source = source,
        reason = reason.reportedAs,
        sizeBytes = file.sizeBytes,
      )
    )
  }
}

/**
 * The model's failure reasons as the taxonomy's. Here rather than on either enum:
 * `feature/datalog/model` has no analytics dependency, and `core/analytics` must not learn what a
 * data log is.
 */
private val ImportFailure.reportedAs: DataLogImportFailureReason
  get() = when (this) {
    ImportFailure.UNREADABLE -> DataLogImportFailureReason.UNREADABLE
    ImportFailure.UNRECOGNIZED -> DataLogImportFailureReason.UNRECOGNIZED
    ImportFailure.DUPLICATE -> DataLogImportFailureReason.DUPLICATE
    ImportFailure.PARSE_ERROR -> DataLogImportFailureReason.PARSE_ERROR
  }
