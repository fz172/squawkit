package dev.fanfly.wingslog.core.ui.adaptive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import org.junit.Test

class ThingIconsTest {

  @Test
  fun everyShippedPresetMapsToItsOwnIcon() {
    // The keys are authored in the .textproto assets and the vectors are here, with nothing
    // connecting them — a preset whose key nobody mapped renders as the generic fallback and
    // still looks deliberate, which is exactly the failure that would ship unnoticed.
    val icons = CanonicalTemplates.ALL.associate { it.id to thingIcon(it.icon) }

    assertThat(icons.values.filter { it == Icons.Filled.Category })
      .hasSize(1)
    assertThat(icons.getValue("custom")).isEqualTo(Icons.Filled.Category)
    assertThat(icons.values.toSet()).hasSize(CanonicalTemplates.ALL.size)
  }

  @Test
  fun anUnknownKeyFallsBackRatherThanFailing() {
    // A template published after this build ships can name an icon we have never heard of.
    assertThat(thingIcon("hot_air_balloon")).isEqualTo(Icons.Filled.Category)
    assertThat(thingIcon("")).isEqualTo(Icons.Filled.Category)
  }
}
