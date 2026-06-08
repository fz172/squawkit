package dev.fanfly.wingslog.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import wingslog.webapp.generated.resources.Res
import wingslog.webapp.generated.resources.noto_color_emoji

/**
 * Web-only emoji support. Skia (Skiko) on the web has no OS-level font fallback, so any glyph
 * absent from the bundled brand fonts (Space Grotesk / JetBrains Mono) — emoji, symbols — renders
 * as tofu. Android and iOS get this fallback from the OS for free; the web does not.
 *
 * We preload Noto Color Emoji and register it as a fallback in the active [FontFamily] resolver so
 * missing glyphs resolve, both in fixed UI (e.g. the onboarding wave) and in user-entered text.
 *
 * [content] is gated until the fallback is registered: registering it does not invalidate the
 * measurement of already-laid-out text, so composing afterward is the reliable way to guarantee no
 * tofu (re-keying the whole tree instead would reset navigation state). The wait shows the host
 * page's brand background (`#001849` in index.html) — no white flash — and the font is a
 * same-origin bundled asset that loads in parallel with the JS bundle, so the added delay is small
 * and the browser caches it for subsequent visits.
 */
@OptIn(
  ExperimentalComposeUiApi::class,
  ExperimentalResourceApi::class,
  InternalComposeUiApi::class
)
@Composable
fun EmojiFallbackProvider(content: @Composable () -> Unit) {
  var fallbackReady by remember { mutableStateOf(false) }
  val fontFamilyResolver = LocalFontFamilyResolver.current
  val emojiFont = preloadFont(Res.font.noto_color_emoji).value

  LaunchedEffect(fontFamilyResolver, emojiFont) {
    if (emojiFont != null) {
      fontFamilyResolver.preload(FontFamily(listOf(emojiFont)))
      fallbackReady = true
    }
  }

  if (fallbackReady) {
    content()
  }
}
