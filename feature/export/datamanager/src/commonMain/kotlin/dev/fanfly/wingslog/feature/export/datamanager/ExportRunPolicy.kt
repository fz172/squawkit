package dev.fanfly.wingslog.feature.export.datamanager

/**
 * What happens to an in-flight export when the app leaves the foreground.
 *
 * iOS suspends a backgrounded app within seconds, so an export that keeps running there stalls
 * on the upload step and is killed at the OS's discretion. Stopping it deliberately and asking the
 * user to start again is the honest behaviour (#343). Android keeps the coroutine alive until
 * process death, so it is left alone until a WorkManager-backed runner lands.
 */
data class ExportRunPolicy(val stopWhenBackgrounded: Boolean)
