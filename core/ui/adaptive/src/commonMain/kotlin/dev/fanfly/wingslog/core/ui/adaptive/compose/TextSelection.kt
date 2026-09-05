package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the host wants free text selection (drag to select, copy) in its content, the way a web
 * page has it. Web sets this; the mobile hosts leave it off so a long-press on a label keeps its
 * native meaning. [TextSelectionLayer] does nothing while it is off.
 */
val LocalTextSelectionLayers = staticCompositionLocalOf { false }

/**
 * Starts a selection scope for one layout root.
 *
 * Compose foundation can only select across text that shares a layout root with its
 * `SelectionContainer`. Popups — dialogs, sheets, menus — draw in a root of their own, and a text
 * inside one that inherits the outer scope crashes on mouse-down ("layouts are not part of the same
 * hierarchy"). So the host wraps its content in this once, and every popup starts a scope of its
 * own: this where the popup has a single content slot, or the popups in
 * `core.ui.common.compose.SelectionSafePopups` where it does not. A hook rejects raw popup imports.
 */
@Composable
fun TextSelectionLayer(content: @Composable () -> Unit) {
  if (LocalTextSelectionLayers.current) SelectionContainer(content = content) else content()
}
