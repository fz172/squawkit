package dev.fanfly.wingslog.feature.tasks.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.ComponentType
import org.junit.Test

class ComponentSlotsTest {

  @Test
  fun aSlotMapsToTheFrozenComponentEnumOnlyOnTheAirplanePreset() {
    val airplane = AirplaneTemplate.TEMPLATE
    assertThat(componentTypeForSlot(SlotKeys.ENGINE, airplane)).isEqualTo(ComponentType.COMPONENT_ENGINE)
    assertThat(componentTypeForSlot(SlotKeys.PROPELLER, airplane)).isEqualTo(ComponentType.COMPONENT_PROPELLER)
    assertThat(componentTypeForSlot("", airplane)).isEqualTo(ComponentType.COMPONENT_AIRFRAME)
    assertThat(componentTypeForSlot(SlotKeys.ENGINE, CanonicalTemplates.HOME))
      .isEqualTo(ComponentType.COMPONENT_UNKNOWN)
  }

  @Test
  fun aComponentMapsBackToItsSlotAndTheAirframeToTheThingItself() {
    assertThat(slotKeyFor(ComponentType.COMPONENT_ENGINE)).isEqualTo(SlotKeys.ENGINE)
    assertThat(slotKeyFor(ComponentType.COMPONENT_PROPELLER)).isEqualTo(SlotKeys.PROPELLER)
    assertThat(slotKeyFor(ComponentType.COMPONENT_AIRFRAME)).isEmpty()
    assertThat(slotKeyFor(ComponentType.COMPONENT_UNKNOWN)).isEmpty()
  }
}
