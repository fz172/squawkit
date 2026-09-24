package dev.fanfly.wingslog.core.ui.sheet

import androidx.compose.runtime.compositionLocalOf

/** How a record's detail sheet presents: on its own over the app, or inline as a scaffold's pane. */
enum class DetailPresentation { Sheet, Pane }

val LocalDetailPresentation = compositionLocalOf { DetailPresentation.Sheet }
