package dev.fanfly.wingslog.feature.login

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getString
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.apple_logo
import wingslog.feature.login.generated.resources.continue_without_account
import wingslog.feature.login.generated.resources.google_logo
import wingslog.feature.login.generated.resources.ic_apple
import wingslog.feature.login.generated.resources.ic_google_rd_na
import wingslog.feature.login.generated.resources.legal_disclaimer
import wingslog.feature.login.generated.resources.login_need_account
import wingslog.feature.login.generated.resources.login_signing_in_creates
import wingslog.feature.login.generated.resources.mission_statement
import wingslog.feature.login.generated.resources.privacy_notice
import wingslog.feature.login.generated.resources.sign_in_with_apple
import wingslog.feature.login.generated.resources.sign_in_with_email
import wingslog.feature.login.generated.resources.sign_in_with_google
import wingslog.feature.login.generated.resources.support_link

/**
 * Resolves every string the sign-in card draws, so it can paint complete on its first frame.
 *
 * Needed because of how compose-resources behaves on web. `stringResource()` returns `""` for a
 * resource that is not cached yet and completes asynchronously — but the canvas is not repainted
 * when it does, so the card renders with blank button labels and no provider marks and stays that
 * way until some input event happens to force a frame. Moving the mouse fixes it, which is not a
 * thing to ask of someone looking at a sign-in page.
 *
 * The cache is per resource, not per file: warming one string does not warm its neighbours, so the
 * list has to be explicit. It lives here rather than in the web host so it sits beside the strings
 * it names — a label added to the card and not added here comes back as a blank button.
 *
 * Only the web host needs to call this ([WebApp] does, before it renders anything); Android and iOS
 * resolve resources synchronously and are unaffected.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun warmLoginCardResources(environment: ResourceEnvironment) {
  val strings = listOf(
    Res.string.mission_statement,
    Res.string.sign_in_with_google,
    Res.string.sign_in_with_apple,
    Res.string.sign_in_with_email,
    Res.string.continue_without_account,
    Res.string.google_logo,
    Res.string.apple_logo,
    Res.string.legal_disclaimer,
    Res.string.privacy_notice,
    Res.string.support_link,
    Res.string.login_need_account,
    Res.string.login_signing_in_creates,
  )
  for (string in strings) {
    runCatching { getString(environment, string) }
  }

  // The provider marks are resources too, and miss the same way — a button whose icon is absent
  // reads as a broken button, not a loading one.
  for (drawable in listOf(Res.drawable.ic_google_rd_na, Res.drawable.ic_apple)) {
    runCatching { getDrawableResourceBytes(environment, drawable) }
  }
}
