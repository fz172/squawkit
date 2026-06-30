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
 * We register Noto Color Emoji as a fallback in the active [FontFamily] resolver so missing glyphs
 * resolve, both in fixed UI (e.g. the onboarding wave) and in user-entered text.
 *
 * The emoji font is ~10 MB, so we never block first paint on it: [content] (login included) renders
 * immediately, and the font is loaded/registered lazily *after* the first composition. This keeps it
 * off the initial critical path — it no longer competes with the JS bundle and the small brand fonts
 * while the login screen is loading. The login screen has no emoji; any missing glyph laid out before
 * the fallback registers resolves on its next layout pass, and the browser caches the font for
 * subsequent visits.
 */
@Composable
fun EmojiFallbackProvider(content: @Composable () -> Unit) {
  // Defer the heavy emoji-font download until after the app's first frame is composed, so it never
  // delays the login screen from displaying.
  var loadEmojiFallback by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { loadEmojiFallback = true }

  if (loadEmojiFallback) {
    EmojiFallbackRegistrar()
  }
  content()
}

@OptIn(
  ExperimentalComposeUiApi::class,
  ExperimentalResourceApi::class,
  InternalComposeUiApi::class
)
@Composable
private fun EmojiFallbackRegistrar() {
  val fontFamilyResolver = LocalFontFamilyResolver.current
  val emojiFont = preloadFont(Res.font.noto_color_emoji).value

  LaunchedEffect(fontFamilyResolver, emojiFont) {
    if (emojiFont != null) {
      fontFamilyResolver.preload(FontFamily(listOf(emojiFont)))
    }
  }
}
