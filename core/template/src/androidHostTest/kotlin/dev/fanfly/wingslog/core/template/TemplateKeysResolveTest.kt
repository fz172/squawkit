package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * That every key the baked-in presets declare resolves — a wrong one is a broken screen, not a
 * failed build. The rules live in [structuralProblems] (#740); this asserts the presets pass them.
 */
class TemplateKeysResolveTest {

  // Not the registry's pool: it filters now, so a broken preset would vanish and this go green.
  private val pool: List<ThingTemplate> = CanonicalTemplates.ALL

  @Test
  fun thePoolIsNotEmpty() {
    // Every assertion below is vacuously true over an empty pool.
    assertThat(pool).isNotEmpty()
  }

  @Test
  fun everyPresetIsStructurallyValid() {
    // The failure message names the preset and the offending key.
    val problems = pool.flatMap { it.structuralProblems() }

    assertThat(problems).isEmpty()
  }

  @Test
  fun noTwoTemplatesShareAnId() {
    // Not in `structuralProblems`, which judges one template at a time. `byId` keeps the last.
    val duplicates = pool.map { it.id }
      .groupingBy { it }
      .eachCount()
      .filterValues { it > 1 }.keys

    assertThat(duplicates).isEmpty()
  }

  @Test
  fun theRegistryOffersEveryPresetThisBuildShips() {
    // The other side of the filter (#740): a refused preset is one missing from the picker.
    val offered =
      BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE).canonical()
        .map { it.id }

    assertThat(offered).containsExactlyElementsIn(pool.map { it.id })
  }

  /**
   * The coupling that is left: `ThingInflater` names a new Thing from `tail_number`, else make and
   * model. #729 took the slot keys and #739 the casing rule; these four are what remain.
   */
  @Test
  fun theAirplaneEditFormUsesOnlySpecKeysTheTemplateDeclares() {
    val airplane = pool.single { it.id == AirplaneTemplate.ID }
    val declared = airplane.spec_fields.map { it.key }
      .toSet()

    val emitted = setOf("make", "model", "serial", "tail_number")

    assertThat(declared).containsAtLeastElementsIn(emitted)
  }
}
