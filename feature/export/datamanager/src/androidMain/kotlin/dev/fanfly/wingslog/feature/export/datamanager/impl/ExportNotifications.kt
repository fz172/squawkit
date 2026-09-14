package dev.fanfly.wingslog.feature.export.datamanager.impl

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeepLinks
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.R

/**
 * The export's tray presence on Android (#343): the foreground-service progress notification while
 * `ExportWorker` runs, and a finished/failed one afterwards. Every one of them opens the export
 * screen through [ExportDeepLinks], the way `AndroidLocalNotifier` routes taps through
 * `NotificationTapRouter`.
 *
 * Built here rather than through `LocalNotifier` because `ForegroundInfo` needs the `Notification`
 * object itself, and progress updates need to repaint the same slot in place.
 */
class ExportNotifications(private val context: Context) {

  private val manager = NotificationManagerCompat.from(context)

  init {
    manager.createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_LOW)
        .setName(context.getString(R.string.export_notification_channel_name))
        .setDescription(context.getString(R.string.export_notification_channel_description))
        .build()
    )
  }

  fun foregroundInfo(progress: ExportProgress.Running): ForegroundInfo = ForegroundInfo(
    PROGRESS_ID,
    progressNotification(progress),
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
  )

  fun updateProgress(progress: ExportProgress.Running) {
    post(PROGRESS_ID, progressNotification(progress))
  }

  fun postFinished(success: ExportProgress.Success) {
    post(
      RESULT_ID,
      base()
        .setContentTitle(context.getString(R.string.export_notification_done_title))
        .setContentText(
          context.getString(R.string.export_notification_done_body, success.fileName)
        )
        .setAutoCancel(true)
        .build(),
    )
  }

  fun postFailed() {
    post(
      RESULT_ID,
      base()
        .setContentTitle(context.getString(R.string.export_notification_failed_title))
        .setContentText(context.getString(R.string.export_notification_failed_body))
        .setAutoCancel(true)
        .build(),
    )
  }

  fun cancelResult() {
    manager.cancel(RESULT_ID)
  }

  private fun progressNotification(progress: ExportProgress.Running) = base()
    .setContentTitle(context.getString(R.string.export_notification_progress_title))
    .setContentText(progress.step.label())
    .setProgress(100, progress.percent, false)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .build()

  private fun base() = NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_export_notification)
    .setContentIntent(tapPendingIntent())

  /** Same shape as `AndroidLocalNotifier.tapPendingIntent`; see there for why not `MainActivity`. */
  private fun tapPendingIntent(): PendingIntent? {
    val launchIntent =
      context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    launchIntent.action = Intent.ACTION_VIEW
    launchIntent.data = Uri.parse(ExportDeepLinks.OPEN_URI)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
      context,
      REQUEST_CODE,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun post(id: Int, notification: android.app.Notification) {
    try {
      manager.notify(id, notification)
    } catch (e: SecurityException) {
      // POST_NOTIFICATIONS denied: the export still runs, it just has no tray entry.
      log.w(e) { "export notification denied at the OS level" }
    }
  }

  private fun ExportProgressStep.label(): String = context.getString(
    when (this) {
      ExportProgressStep.COLLECTING_DATA -> R.string.export_notification_step_collecting
      ExportProgressStep.BUILDING_ARCHIVE -> R.string.export_notification_step_building
      ExportProgressStep.COMPRESSING_ARCHIVE -> R.string.export_notification_step_compressing
      ExportProgressStep.SAVING_FILE -> R.string.export_notification_step_saving
      ExportProgressStep.UPLOADING_ARCHIVE -> R.string.export_notification_step_uploading
    }
  )

  private companion object {
    val log = Logger.withTag("ExportNotifications")

    /** Never change: Android keys the user's per-channel settings by this string (see #663). */
    const val CHANNEL_ID = "export"
    const val PROGRESS_ID = 4301
    const val RESULT_ID = 4302
    const val REQUEST_CODE = 4300
  }
}
