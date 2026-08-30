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
 *
 * ---
 *
 * ## THIS IS ENGLISH-ONLY, AND SO IS THE MODEL BEHIND IT
 *
 * Not "English-first" or "not yet translated" — the approach does not generalise, and the ceiling
 * is [Noun] and the substitution pattern rather than anything fixable in this file. The app ships
 * one locale today (31 `strings.xml` files, all under `values`, no variants), so nothing here is
 * currently wrong. It is a blocker to reach for the day localisation is on the table.
 *
 * **[Noun] cannot express most languages' plurals.** It has exactly two forms. Russian and Polish
 * select among three depending on the number (1 / 2–4 / 5+); Arabic has a dual distinct from both
 * singular and plural; Chinese and Japanese inflect for number not at all. CLDR defines six plural
 * categories, and a two-field message can carry two of them.
 *
 * **`article` assumes an article, preceding, separable, and invariant.** Russian, Chinese, and
 * Japanese have no indefinite article at all, so [withArticle] emits a stray word. Swedish and
 * Norwegian attach articles as suffixes. German and Spanish inflect them by gender — and in
 * German also by case, so "a squawk" is *ein* or *einen* depending on where it lands in the
 * sentence, which is information this function is not given and could not use.
 *
 * **Title case is an English convention.** German capitalises every noun regardless of position;
 * French uses sentence case for titles; Chinese, Japanese, Arabic, Hebrew, and Thai have no case
 * at all, making [titleCase] and [sentenceCase] silent no-ops there. The minor-word list is a list
 * of English words.
 *
 * **The deepest limit is not in this file.** Substituting a noun into a fixed sentence frame
 * assumes the rest of the sentence does not change when the noun does. In any language with
 * grammatical gender or case that is false: German "Diese*n* Squawk löschen?" needs the
 * determiner to agree with the noun's gender, so `"Delete %1${'$'}s?"` cannot be filled from a
 * bare noun no matter how the noun is formatted. Fixing that means per-template *sentences*, not
 * per-template nouns — a different design, not a bigger [Noun].
 *
 * If localisation happens, the honest options are grammatical-gender and plural-category fields on
 * [Noun] plus a real ICU MessageFormat pipeline, or accepting per-locale full strings for anything
 * a lexicon touches. Growing this object will not get there.
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
