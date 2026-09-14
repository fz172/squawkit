package dev.fanfly.wingslog.feature.export.datamanager.impl

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Runs one export as a long-running WorkManager job with a foreground-service notification
 * (#343), so it outlives the export screen and the app's foreground state. Progress is echoed into
 * `setProgress` and the outcome into the output `Data`, which is how `WorkManagerExportJobCoordinator`
 * reconstructs the job for the screen — including after process death.
 */
class ExportWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

  private val exportManager: ExportManager by inject()
  private val notifications: ExportNotifications by inject()

  override suspend fun doWork(): Result {
    val request = ExportWorkData.decodeRequest(inputData) ?: return Result.failure()
    val first = ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 0)
    setProgress(ExportWorkData.encode(request, first))
    try {
      setForeground(notifications.foregroundInfo(first))
    } catch (e: IllegalStateException) {
      // Android 12+ refuses a foreground service start from the background. The export was
      // submitted from the screen, so this is rare; the work still runs, without the tray entry.
      log.w(e) { "export could not start as a foreground service" }
    }

    var terminal: ExportProgress? = null
    try {
      exportManager.exportLogs(request)
        .collect { progress ->
          when (progress) {
            is ExportProgress.Running -> {
              setProgress(ExportWorkData.encode(request, progress))
              notifications.updateProgress(progress)
            }

            else -> terminal = progress
          }
        }
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      log.e(t) { "export failed" }
      terminal = ExportProgress.Error(t.message.orEmpty(), t)
    }

    return when (val outcome = terminal ?: ExportProgress.Error("")) {
      is ExportProgress.Success -> {
        notifications.postFinished(outcome)
        Result.success(ExportWorkData.encode(request, outcome))
      }

      else -> {
        notifications.postFailed()
        Result.failure(ExportWorkData.encode(request, outcome))
      }
    }
  }

  private companion object {
    val log = Logger.withTag("ExportWorker")
  }
}
