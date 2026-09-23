package dev.fanfly.wingslog.feature.dashboard.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.empty_add_thing
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * Host entry point for the adaptive shell's **per-thing** section bodies: maps a [dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection]
 * (+ optional ambient [thingId]) to the right content. Both hosts (`AppEntry`, `WebApp`) call this
 * from the shell's `sectionContent` slot for everything except [dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection.SETTINGS], which is
 * global and rendered by the host directly (it depends on `feature:settings`).
 *
 * - per-thing sections → [ThingSectionContent], or an empty state when no thing exists.
 */
@Composable
fun ShellSectionBody(
  section: ShellSection,
  thingId: String?,
  navController: NavController,
  onNavigateToSection: (ShellSection) -> Unit,
  /** Switch to an associated record's section and scroll to it. The host owns the target: each
   * section is its own composition, so state remembered here would not survive the switch. */
  onJumpToRecord: (RecordJump) -> Unit = {},
  /**
   * A record the host wants scrolled to and highlighted in [section]'s list — currently a tapped
   * urgency notification (notifications design §5.3). Interpreted against [section], which the host
   * sets to match the record's kind, so this needs no type of its own. [onScrollTargetConsumed] is
   * called once it has been handed to the list, so the host can drop it and not re-trigger the jump
   * every time the pilot returns to this section.
   */
  scrollToRecordId: String? = null,
  onScrollTargetConsumed: () -> Unit = {},
  /** A guest asked to link an account (data log design §8.4): the host opens Settings on its sheet. */
  onLinkAccount: () -> Unit = {},
) {
  if (thingId != null) {
    ThingSectionContent(
      thingId = thingId,
      section = section,
      navController = navController,
      onNavigateToSection = onNavigateToSection,
      onJumpToRecord = onJumpToRecord,
      scrollToRecordId = scrollToRecordId,
      onScrollTargetConsumed = onScrollTargetConsumed,
      onLinkAccount = onLinkAccount,
    )
  } else {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Text(
        stringResource(
          CoreRes.string.empty_add_thing,
          LexiconFormatter.withArticle(LocalThingLexicon.current.thingNoun),
        ),
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}
