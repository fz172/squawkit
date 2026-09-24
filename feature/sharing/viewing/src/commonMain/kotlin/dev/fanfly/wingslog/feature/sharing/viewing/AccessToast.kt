package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_access_removed
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_code_cancelled
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_link_copied
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_role_updated

/**
 * A transient confirmation shown after an action (e.g. "Invite link copied"). Kept as an enum
 * rather than a plain string so the ViewModel — which has no dependency on compose resources —
 * can set it without hardcoding unlocalized text; the screen resolves it to a string when shown.
 */
enum class AccessToast { LINK_COPIED, ROLE_UPDATED, ACCESS_REMOVED, CODE_CANCELLED }

@Composable
internal fun AccessToast.text(): String = when (this) {
  AccessToast.LINK_COPIED -> stringResource(Res.string.manage_access_toast_link_copied)
  AccessToast.ROLE_UPDATED -> stringResource(Res.string.manage_access_toast_role_updated)
  AccessToast.ACCESS_REMOVED -> stringResource(Res.string.manage_access_toast_access_removed)
  AccessToast.CODE_CANCELLED -> stringResource(Res.string.manage_access_toast_code_cancelled)
}
