package dev.fanfly.wingslog.feature.sharing.viewing.redeem

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_role_owner

@Composable
internal fun roleLabel(role: ShareRole): String = when (role) {
  ShareRole.OWNER -> stringResource(Res.string.redeem_role_owner)
  ShareRole.TECHNICIAN -> LexiconFormatter.withArticle(LocalThingLexicon.current.technicianNoun)
}
