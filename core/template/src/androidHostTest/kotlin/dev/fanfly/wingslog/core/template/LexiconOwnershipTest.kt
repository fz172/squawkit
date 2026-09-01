package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.Noun
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * That the words belong to the app, not to the Thing.
 *
 * A lexicon is written against the screens of a particular release — it is UI, the way
 * `strings.xml` is. Storing a copy on every Thing made each one a fork of the app's vocabulary
 * that no later release could correct: adding `short_plural` to every preset left existing Things
 * rendering the old full plural in the navigation bar, with the corrected asset sitting unused in
 * the binary. The structural half of a template stays DNA, because it is coupled to stored data.
 */
class LexiconOwnershipTest {

  private val registry = BakedInTemplateRegistry(appVersionCode = 1_000)

  @Test
  fun aThingCarryingStaleWordsRendersTheCurrentOnes() {
    // Exactly the shipped bug: DNA frozen before `short_plural` existed.
    val stale = AirplaneTemplate.TEMPLATE.copy(
      lexicon = AirplaneTemplate.AIRPLANE_LEXICON.copy(
        task = Noun(singular = "old task", plural = "old tasks", article = "a"),
      ),
    )

    val lexicon = registry.lexiconFor(stale)

    assertThat(lexicon.task?.plural).isEqualTo("maintenance tasks")
    assertThat(lexicon.task?.short_plural).isEqualTo("Maint.")
  }

  @Test
  fun everyPresetResolvesItsOwnWordsById() {
    CanonicalTemplates.ALL.forEach { template ->
      assertThat(registry.lexiconFor(template)).isEqualTo(template.lexicon)
    }
  }

  @Test
  fun aPresetThisBuildDoesNotCarryFallsBackToItsOwnWords() {
    // A preset a newer release introduced. Its DNA holds the only words that describe it, and the
    // Thing renders degraded anyway — the right nouns there beat generic ones.
    val unknown = CanonicalTemplates.HOME.copy(id = "greenhouse")

    assertThat(registry.lexiconFor(unknown)).isEqualTo(CanonicalTemplates.HOME.lexicon)
  }

  @Test
  fun noTemplateMeansTheGenericWords() {
    assertThat(registry.lexiconFor(null)).isEqualTo(GenericLexicon.LEXICON)
  }

  @Test
  fun theInflaterDoesNotStoreWordsOnTheThing() {
    // Nothing migrates: old Things keep a lexicon nobody reads, and new ones stop carrying one.
    val inflated =
      ThingInflater.inflate(Thing(id = "t"), AirplaneTemplate.TEMPLATE)

    assertThat(inflated.template).isNotNull()
    assertThat(inflated.template?.lexicon).isNull()
    // The structural half is still DNA — it is coupled to the components and spec this Thing stores.
    assertThat(inflated.template?.component_slots)
      .isEqualTo(AirplaneTemplate.TEMPLATE.component_slots)
    assertThat(inflated.template?.capabilities)
      .isEqualTo(AirplaneTemplate.AIRPLANE_CAPABILITIES)
  }

  @Test
  fun anExistingThingsStoredWordsAreLeftAloneRatherThanRewritten() {
    // Ignored, not erased. A migration would be a write to every Thing on every account for
    // something reading past already handles.
    val existing = Thing(id = "t", template = AirplaneTemplate.TEMPLATE)

    val inflated = ThingInflater.inflate(existing, AirplaneTemplate.TEMPLATE)

    assertThat(inflated.template?.id).isEqualTo("airplane")
    assertThat(registry.lexiconFor(inflated.template))
      .isEqualTo(AirplaneTemplate.AIRPLANE_LEXICON)
  }
}
