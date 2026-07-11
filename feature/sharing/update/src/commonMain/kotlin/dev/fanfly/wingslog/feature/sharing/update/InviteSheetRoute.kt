package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.sharing.viewing.InviteSheetScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_title

/** Binds [InviteSheetViewModel] to [InviteSheetScreen], wiring the platform share sheet + clipboard. */
@Composable
fun InviteSheetRoute(navController: NavController) {
  val viewModel = koinViewModel<InviteSheetViewModel>()
  val state by viewModel.uiState.collectAsState()
  val linkSharer = rememberLinkSharer()
  val clipboard = LocalClipboardManager.current
  val chooserTitle = stringResource(Res.string.invite_title)

  InviteSheetScreen(
    state = state,
    onRoleSelected = viewModel::selectRole,
    onCreate = viewModel::createInvite,
    onShare = { url -> linkSharer.shareLink(url, chooserTitle) },
    onCopy = { url -> clipboard.setText(AnnotatedString(url)) },
    onCancelInvite = viewModel::cancelInvite,
    onToggleExpand = viewModel::toggleExpand,
    onDismiss = { navController.popBackStack() },
  )
}
