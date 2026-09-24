package dev.fanfly.wingslog.core.ui.swipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * One open card per list. Remembered by the list and passed to every card; a card whose [key]
 * stops being [openKey] animates itself closed. Not saved across process death on purpose — a
 * revealed card is not state worth surviving it.
 */
@Stable
class SwipeRevealController {
  var openKey: Any? by mutableStateOf(null)
    private set

  fun open(key: Any) {
    openKey = key
  }

  fun close() {
    openKey = null
  }

  internal fun closeIf(key: Any) {
    if (openKey == key) openKey = null
  }

  /** Closes the open card on the first vertical scroll delta. Install on the list with `Modifier.nestedScroll`. */
  val closeOnScroll: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(
      available: Offset,
      source: NestedScrollSource
    ): Offset {
      if (available.y != 0f) close()
      return Offset.Zero
    }
  }
}

@Composable
fun rememberSwipeRevealController(): SwipeRevealController =
  remember { SwipeRevealController() }
