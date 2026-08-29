package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Noun

/**
 * Renders a [Noun] into the form a particular string needs (PRD §10).
 *
 * The app's ~150 noun-substitutable strings become format strings — `"Add aircraft"` becomes
 * `"Add %1${'$'}s"` — and something has to decide whether the substituted word is "aircraft",
 * "Aircraft", "aircraft", "an aircraft", or "Aircraft on Ground". That is this.
 *
 * **Not a `.capitalize()` wrapper.** Three of the four transforms have a case that a naive
 * implementation gets wrong, and each of those cases is in the shipped aviation lexicon or one
 * word away from it:
 *
 * - **Article** is read from [Noun.article], never derived. "an hour" and "a unicycle" both defeat
 *   first-letter-is-a-vowel, and the airplane lexicon needs "an aircraft".
 * - **Plural** is read from [Noun.plural], never suffixed. "aircraft" is its own plural, and
 *   "aircraft" + "s" is wrong on the very first preset.
 * - **Title case** is not per-word capitalisation. "Aircraft on Ground" is the shipped string;
 *   "Aircraft On Ground" is not.
 *
 * Every transform here is **locale-invariant on purpose**. `String.uppercase()` without a locale
 * uppercases Turkish dotted-i to a dotless capital, so a Turkish device would render a subtly
 * wrong noun. These use [Char.uppercaseChar], which is defined per-character and does not consult
 * a locale.
 */
object LexiconFormatter {

  /**
   * Words that stay lowercase inside a title unless they land first or last.
   *
   * Deliberately short. A long list is more likely to lowercase something a template author meant
   * to capitalise than to fix a real case, and the corpus this has to satisfy is one aviation
   * lexicon — not English generally.
   */
  private val TITLE_MINOR_WORDS = setOf(
    "a", "an", "and", "as", "at", "but", "by", "for", "in", "nor",
    "of", "on", "or", "the", "to", "up", "via",
  )

  /** `aircraft` → `aircraft`. The plural the template declares, never a suffix rule. */
  fun plural(noun: Noun): String = noun.plural

  /** `aircraft` → `an aircraft`. The article the template declares, never a vowel check. */
  fun withArticle(noun: Noun): String =
    if (noun.article.isEmpty()) noun.singular else "${noun.article} ${noun.singular}"

  /** `an aircraft` → `An aircraft`. */
  fun sentenceCase(text: String): String =
    if (text.isEmpty()) text else text[0].uppercaseChar() + text.substring(1)

  /** `aircraft` → `Aircraft`. */
  fun sentenceCase(noun: Noun): String = sentenceCase(noun.singular)

  /** `aircraft` → `Aircraft`, plural form. */
  fun sentenceCasePlural(noun: Noun): String = sentenceCase(noun.plural)

  /**
   * `aircraft on ground` → `Aircraft on Ground`.
   *
   * First and last words are always capitalised, whatever they are — "The" opening a title is
   * capitalised, and a title ending in "of" (rare, but not impossible in a template author's
   * hands) should not trail a lowercase word.
   *
   * Words already carrying an interior capital are left alone: "AOG" must not become "Aog", and
   * that is a real value in the airplane lexicon rather than a hypothetical.
   */
  fun titleCase(text: String): String {
    if (text.isEmpty()) return text
    val words = text.split(" ")
    return words.mapIndexed { index, word ->
      when {
        word.isEmpty() -> word
        // Preserve deliberate casing: acronyms, and anything the author capitalised interiorly.
        word.any { it.isUpperCase() } -> word
        index == 0 || index == words.lastIndex -> sentenceCase(word)
        word.lowercase() in TITLE_MINOR_WORDS -> word
        else -> sentenceCase(word)
      }
    }.joinToString(" ")
  }

  /** `aircraft` → `Aircraft`; `aircraft on ground` → `Aircraft on Ground`. */
  fun titleCase(noun: Noun): String = titleCase(noun.singular)

  /** Plural, title-cased: what a section header or a tab label wants. */
  fun titleCasePlural(noun: Noun): String = titleCase(noun.plural)
}
