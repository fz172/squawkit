package dev.fanfly.wingslog.feature.ads.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.feature.ads.model.AdLayoutTier
import dev.fanfly.wingslog.feature.ads.model.AdSlotFormat
import dev.fanfly.wingslog.feature.ads.model.AdSlotKey
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import org.junit.Test

/**
 * The decision `AdSlot` makes, exercised end to end at the layer below Compose.
 *
 * This exists because the previous regression test tested the wrong layer. It called the counter
 * directly, the counter was already correct, and it passed — while the app stayed broken, because
 * `AdSlot` consulted remaining headroom *first* and returned before ever asking. Tests that stop at
 * the collaborator cannot catch a caller that never calls it.
 *
 * So this mirrors the real sequence: ask what the tier wants, reserve, render whatever was granted.
 */
class AdSlotRenderFlowTest {

  private val foreground = AppForegroundObserver()
  private val counter = AdSessionCounter(foreground)

  init {
    foreground.onEnterForeground()
  }

  /** Exactly what AdSlot does: desiredUnits -> reserve -> forGrant. Null means render nothing. */
  private fun render(key: AdSlotKey, tier: AdLayoutTier = AdLayoutTier.COMPACT): AdSlotFormat? =
    AdSlotFormat.forGrant(counter.reserve(key, AdSlotFormat.desiredUnits(tier)))

  @Test
  fun `the reported repro - three surfaces to the cap, then every revisit still renders`() {
    // Logs 3, tasks 1, squawks 1 = the five the pilot saw.
    val logs = (0..2).map { AdSlotKey(AdSurface.LOGS, it) }
    val task = AdSlotKey(AdSurface.TASKS, 0)
    val squawk = AdSlotKey(AdSurface.SQUAWKS, 0)
    val seen = logs + task + squawk

    seen.forEach { assertThat(render(it)).isNotNull() }
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)

    // Switching tabs disposes and recomposes every slot. All five must still render.
    repeat(3) {
      seen.forEach { key ->
        assertThat(render(key)).isNotNull()
      }
    }
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `a slot never granted still renders nothing at the cap`() {
    repeat(AdSessionCounter.CAP) { render(AdSlotKey(AdSurface.LOGS, it)) }

    // The cap must still bind for slots that were not already showing an ad.
    assertThat(render(AdSlotKey(AdSurface.LOGS, 99))).isNull()
  }

  @Test
  fun `a wide slot granted only one unit renders centred rather than nothing`() {
    // Four singles leave one unit; a two-up slot asks for two, gets one, and renders SINGLE.
    repeat(AdSessionCounter.CAP - 1) { render(AdSlotKey(AdSurface.LOGS, it)) }

    val wide = AdSlotKey(AdSurface.SQUAWKS, 0)
    assertThat(render(wide, AdLayoutTier.WIDE)).isEqualTo(AdSlotFormat.SINGLE)
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)

    // And it keeps rendering that single unit when revisited.
    assertThat(render(wide, AdLayoutTier.WIDE)).isEqualTo(AdSlotFormat.SINGLE)
  }

  @Test
  fun `revisiting costs no budget`() {
    val key = AdSlotKey(AdSurface.TASKS, 0)
    render(key)
    val afterFirst = counter.displayed.value

    repeat(20) { render(key) }

    assertThat(counter.displayed.value).isEqualTo(afterFirst)
  }
}
