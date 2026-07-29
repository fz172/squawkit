package dev.fanfly.wingslog.core.appinfo

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Gates Kermit output by build flavor (#276).
 *
 * Debug and verbose logs deliberately carry identifiers that must never reach a release-visible
 * log — aircraft ids, other accounts' uids, remote blob paths, signed download URLs with bearer
 * tokens. The redaction added in #246 keeps those at `d`/`v` precisely *because* debug is supposed
 * to be absent from release, so that rule has to actually hold: a release build drops everything
 * below [Severity.Info].
 *
 * Call once per host at startup, **before `startKoin`**, so logging emitted while the graph is
 * being constructed is gated too. Every `Logger.withTag(...)` in the app is derived from the
 * default [Logger]'s config instance, so this applies to loggers already constructed as well as to
 * ones created afterwards.
 *
 * This gates *emission*. It does not remove the log strings from the shipped binary.
 */
fun configureLogging(isDeveloperBuild: Boolean) {
  Logger.setMinSeverity(if (isDeveloperBuild) Severity.Verbose else Severity.Info)
}
