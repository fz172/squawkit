package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.comments.sharedassets.generated.resources.comments_tab
import wingslog.feature.squawk.update.generated.resources.Res
import wingslog.feature.squawk.update.generated.resources.squawk_tab_details
import wingslog.feature.comments.sharedassets.generated.resources.Res as CommentsRes

/**
 * The squawk form's tabs, as identities rather than positions — the same reasoning as
 * `TaskFormTab`: a tab that can be absent must not renumber the ones after it.
 *
 * Only [DETAILS] exists on the add form; see [squawkFormTabsFor].
 */
enum class SquawkFormTab {
  DETAILS,

  /** Edit only — a squawk that does not exist yet has no id for a comment to point at. */
  COMMENTS,
  ;

  /** Stable, locale-independent analytics key. Tied to the identity, not to a position. */
  val analyticsKey: String
    get() = when (this) {
      DETAILS -> "details"
      COMMENTS -> "comments"
    }
}

fun squawkFormTabsFor(isEdit: Boolean): List<SquawkFormTab> =
  SquawkFormTab.entries.filter { it != SquawkFormTab.COMMENTS || isEdit }

@Composable
fun SquawkTabRow(
  tabs: List<SquawkFormTab>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  commentCount: Int = 0,
) {
  IconLabelTabRow(
    tabs = tabs.map { tab ->
      when (tab) {
        SquawkFormTab.DETAILS -> IconLabelTabSpec(
          icon = Icons.Default.Edit,
          label = stringResource(Res.string.squawk_tab_details),
        )

        SquawkFormTab.COMMENTS -> IconLabelTabSpec(
          icon = Icons.Default.Forum,
          label = stringResource(CommentsRes.string.comments_tab),
          badgeCount = commentCount,
        )
      }
    },
    selectedIndex = selectedIndex,
    onSelect = onSelect,
    modifier = modifier,
  )
}
