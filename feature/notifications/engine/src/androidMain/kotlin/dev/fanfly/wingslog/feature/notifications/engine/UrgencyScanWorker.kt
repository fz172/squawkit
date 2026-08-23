package dev.fanfly.wingslog.feature.notifications.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Runs one scheduled [UrgencyScanner.scan]. Every early exit — signed out, notifications off,
 * permission revoked — is a [ScanResult], not a failure: the periodic chain should keep its rhythm
 * so the next scan after the user turns something back on happens on time.
 */
class UrgencyScanWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

  private val scanner: UrgencyScanner by inject()

  override suspend fun doWork(): Result {
    val result = runCatching { scanner.scan(ScanTrigger.SCHEDULED) }
      .getOrElse { e ->
        log.w(e) { "scheduled urgency scan failed" }
        // One backed-off retry inside this period; the next period runs regardless.
        return Result.retry()
      }
    log.d { "scheduled urgency scan: $result" }
    return Result.success()
  }

  private companion object {
    val log = Logger.withTag("UrgencyScanWorker")
  }
}
