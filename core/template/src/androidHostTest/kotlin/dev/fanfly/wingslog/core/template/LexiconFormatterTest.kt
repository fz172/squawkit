package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Noun
import org.junit.Test

/**
 * The formatter's job is to be correct on the cases a naive implementation gets wrong, so those
 * are what this covers. Each one below is either in the shipped aviation lexicon already or is a
 * word a Phase 3 preset plausibly needs.
 */
class LexiconFormatterTest {

  private val aircraft = AirplaneTemplate.AIRPLANE_LEXICON.thing!!
  private val squawk = AirplaneTemplate.AIRPLANE_LEXICON.squawk!!

  // --- article ---

  @Test
  fun articleComesFromTheNounRatherThanTheFirstLetter() {
    // "aircraft" needs "an" and starts with a vowel, so a vowel check would agree by accident.
    // The next two cases are why the field exists.
    assertThat(LexiconFormatter.withArticle(aircraft)).isEqualTo("an aircraft")
  }

  @Test
  fun aVowelInitialNounCanStillTakeA() {
    // "a unicycle" — a plausible Phase 3 preset, and a vowel check renders "an unicycle".
    val unicycle = Noun(singular = "unicycle", plural = "unicycles", article = "a")
    assertThat(LexiconFormatter.withArticle(unicycle)).isEqualTo("a unicycle")
  }

  @Test
  fun aConsonantInitialNounCanStillTakeAn() {
    // "an hour" — the mirror case. Both defeat first-letter rules in opposite directions.
    val hourMeter = Noun(singular = "hour meter", plural = "hour meters", article = "an")
    assertThat(LexiconFormatter.withArticle(hourMeter)).isEqualTo("an hour meter")
  }

  @Test
  fun anEmptyArticleYieldsTheBareNoun() {
    // Languages without indefinite articles, and templates that simply leave it blank. Rendering
    // " aircraft" with a leading space would be worse than rendering the noun alone.
    val bare = Noun(singular = "aircraft", plural = "aircraft", article = "")
    assertThat(LexiconFormatter.withArticle(bare)).isEqualTo("aircraft")
  }

  // --- plural ---

  @Test
  fun pluralComesFromTheNounRatherThanASuffixRule() {
    // "aircraft" is its own plural. A +s rule is wrong on the very first preset.
    assertThat(LexiconFormatter.plural(aircraft)).isEqualTo("aircraft")
    assertThat(LexiconFormatter.plural(squawk)).isEqualTo("squawks")
  }

  // --- sentence case ---

  @Test
  fun sentenceCaseRaisesOnlyTheFirstCharacter() {
    assertThat(LexiconFormatter.sentenceCase(aircraft)).isEqualTo("Aircraft")
    assertThat(LexiconFormatter.sentenceCase("an aircraft")).isEqualTo("An aircraft")
    assertThat(LexiconFormatter.sentenceCasePlural(squawk)).isEqualTo("Squawks")
  }

  @Test
  fun sentenceCaseHandlesEmptyInput() {
    assertThat(LexiconFormatter.sentenceCase("")).isEqualTo("")
  }

  // --- title case ---

  @Test
  fun titleCaseKeepsMinorWordsLowercase() {
    // The shipped string is "Aircraft on Ground", not "Aircraft On Ground". This is the assertion
    // the byte-identical snapshot test would otherwise catch much later and much less clearly.
    assertThat(LexiconFormatter.titleCase("aircraft on ground")).isEqualTo("Aircraft on Ground")
  }

  @Test
  fun titleCaseAlwaysRaisesTheFirstAndLastWord() {
    // A minor word landing first or last is still capitalised — "The Fleet", and a title ending
    // "of" should not trail lowercase.
    assertThat(LexiconFormatter.titleCase("the fleet")).isEqualTo("The Fleet")
    assertThat(LexiconFormatter.titleCase("out of")).isEqualTo("Out Of")
  }

  @Test
  fun titleCasePreservesDeliberateCapitalisation() {
    // "AOG" is a real airplane lexicon value. Lowercasing it to "Aog" would be a visible
    // regression, and any implementation that normalises before capitalising does exactly that.
    assertThat(LexiconFormatter.titleCase("AOG")).isEqualTo("AOG")
    assertThat(LexiconFormatter.titleCase("AOG squawks")).isEqualTo("AOG Squawks")
  }

  @Test
  fun titleCaseHandlesSingleWordsAndEmptyInput() {
    assertThat(LexiconFormatter.titleCase("aircraft")).isEqualTo("Aircraft")
    assertThat(LexiconFormatter.titleCase("")).isEqualTo("")
  }

  @Test
  fun titleCasePluralIsWhatASectionHeaderWants() {
    assertThat(LexiconFormatter.titleCasePlural(squawk)).isEqualTo("Squawks")
    assertThat(LexiconFormatter.titleCasePlural(aircraft)).isEqualTo("Aircraft")
  }

  // --- the airplane lexicon, rendered ---

  @Test
  fun theAirplaneLexiconRendersTheWordsTheAppShipsToday() {
    // Not a formatter test so much as a canary: these are the exact strings the byte-identical
    // snapshot test (#658) will compare against, so a change here is a change to the product.
    val l = AirplaneTemplate.AIRPLANE_LEXICON
    assertThat(LexiconFormatter.titleCase(l.down_status)).isEqualTo("AOG")
    assertThat(LexiconFormatter.titleCase(l.down_status_long)).isEqualTo("Aircraft on Ground")
    assertThat(LexiconFormatter.titleCasePlural(l.squawk!!)).isEqualTo("Squawks")
    assertThat(LexiconFormatter.sentenceCase(l.thing!!)).isEqualTo("Aircraft")
    // The paywall's feature-row label renders straight from here, with no string resource between,
    // so this assertion is that label's only coverage.
    assertThat(LexiconFormatter.titleCase(l.thing!!)).isEqualTo("Aircraft")
  }
}

/**
 * The generic lexicon — what every account-level screen renders, on every account.
 *
 * Worth its own coverage because it is the one lexicon that is *never* a template's, so nothing
 * about a Thing exercises it. On a single-airplane account it still backs settings and the
 * switcher, which means a mistake here is visible in Phase 2 rather than dormant until Phase 3.
 */
class GenericLexiconTest {

  @Test
  fun everyNounAndStringIsPopulated() {
    val l = GenericLexicon.LEXICON
    val nouns = listOf(l.thing, l.squawk, l.task, l.log, l.component, l.technician)
    for (noun in nouns) {
      assertThat(noun).isNotNull()
      assertThat(noun!!.singular).isNotEmpty()
      assertThat(noun.plural).isNotEmpty()
      assertThat(noun.article).isNotEmpty()
    }
    assertThat(
      listOf(
        l.ready_status, l.down_status, l.down_status_long, l.collection_label,
        l.compliance_mandatory, l.compliance_advisory, l.authority_label,
      ),
    ).doesNotContain("")
  }

  @Test
  fun itIsGenericRatherThanAirplaneWithBlanks() {
    // The distinction PRD §8.5 draws: a mixed account falls back to a domain-neutral word, not to
    // one template's word chosen arbitrarily. If this ever equals the airplane lexicon, a boat
    // owner's settings screen starts saying "aircraft".
    assertThat(GenericLexicon.LEXICON).isNotEqualTo(AirplaneTemplate.AIRPLANE_LEXICON)
    assertThat(GenericLexicon.LEXICON.thing!!.singular).isEqualTo("thing")
    assertThat(GenericLexicon.LEXICON.down_status).isNotEqualTo("AOG")
  }

  @Test
  fun itFormatsThroughTheSameRules() {
    // No special-casing: the generic lexicon is a Lexicon like any other.
    val l = GenericLexicon.LEXICON
    assertThat(LexiconFormatter.withArticle(l.squawk!!)).isEqualTo("an issue")
    assertThat(LexiconFormatter.titleCase(l.down_status_long)).isEqualTo("Out of Service")
  }
}
