package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_co_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_owner

/**
 * The hosting owner is *the* owner; anyone else holding the owner role is a co-owner. Same wire
 * role — the distinction is who the thing belongs to, and calling both "Owner" hid that.
 */
@Composable
internal fun roleLabel(role: ShareRole, isHost: Boolean): String = when (role) {
  ShareRole.OWNER ->
    if (isHost) stringResource(Res.string.manage_access_role_owner)
    else stringResource(Res.string.manage_access_role_co_owner)

  ShareRole.TECHNICIAN -> LexiconFormatter.titleCase(LocalThingLexicon.current.technicianNoun)
}
