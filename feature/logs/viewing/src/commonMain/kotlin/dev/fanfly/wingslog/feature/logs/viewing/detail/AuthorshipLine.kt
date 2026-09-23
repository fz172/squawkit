package dev.fanfly.wingslog.feature.logs.viewing.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.sharedassets.generated.resources.log_assigned_by
import wingslog.feature.logs.sharedassets.generated.resources.log_assigned_by_unknown
import wingslog.feature.logs.sharedassets.generated.resources.log_unverified_technician
import wingslog.feature.logs.sharedassets.generated.resources.Res as MaintenanceRes

/**
 * What we can and cannot say about the name above this line.
 *
 * It speaks up in two cases. [LogAuthorship.Assigned] — someone other than the named technician
 * wrote the entry. [LogAuthorship.Unverifiable] — the name was typed by hand and belongs to no
 * account, so nothing stands behind it; a typed name should not sit there looking as settled as a
 * signed one.
 *
 * It stays silent in two. [LogAuthorship.SelfSigned] is the ordinary case — the technician wrote up
 * their own work — and remarking on it would make the exception look like the rule.
 * [LogAuthorship.Unknown] is a revision written before authorship was recorded: it proves nothing
 * either way, and casting doubt on an old entry we simply have no data for would be a lie.
 *
 * The technician's name is the line directly above this one, so it is never repeated here.
 */
@Composable
internal fun AuthorshipLine(authorship: LogAuthorship) {
  val text = when (authorship) {
    is LogAuthorship.Assigned -> authorship.authorName?.let { author ->
      stringResource(MaintenanceRes.string.log_assigned_by, author)
    } ?: stringResource(MaintenanceRes.string.log_assigned_by_unknown)

    is LogAuthorship.Unverifiable ->
      stringResource(MaintenanceRes.string.log_unverified_technician)

    is LogAuthorship.SelfSigned, LogAuthorship.Unknown -> return
  }
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
