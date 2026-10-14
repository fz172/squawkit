package dev.fanfly.wingslog.feature.sharing.viewing.redeem

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_role_contributor
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_role_owner

/**
 * The offered role, as the invitee reads it. Not the lexicon's technician word: the invitee has not
 * joined yet, so the only lexicon in scope is the selected Thing's — not the one being shared.
 */
@Composable
internal fun roleLabel(role: ShareRole): String = when (role) {
  ShareRole.OWNER -> stringResource(Res.string.redeem_role_owner)
  ShareRole.TECHNICIAN -> stringResource(Res.string.redeem_role_contributor)
}
