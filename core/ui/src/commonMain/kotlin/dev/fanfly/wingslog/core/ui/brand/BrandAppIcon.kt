package dev.fanfly.wingslog.core.ui.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The icon's vellum ground, top to bottom, as in `docs/branding/app-icon-record-stack.svg`. */
private val VELLUM = Brush.verticalGradient(listOf(Color.White, Color(0xFFD9E1EC)))

/** The stack's square as a fraction of the tile: the master draws its 570 units at 1.12 in 1024. */
private const val STACK_ON_TILE = 570f * 1.12f / 1024f

/**
 * The app icon as a launcher shows it: the record stack on its vellum tile. It fills [modifier]'s
 * box, which should be square, and leaves the corners to the caller, since every launcher rounds
 * them differently.
 */
@Composable
fun BrandAppIcon(modifier: Modifier = Modifier) {
  Box(modifier = modifier.background(VELLUM), contentAlignment = Alignment.Center) {
    BrandStack(Modifier.fillMaxSize(STACK_ON_TILE))
  }
}
