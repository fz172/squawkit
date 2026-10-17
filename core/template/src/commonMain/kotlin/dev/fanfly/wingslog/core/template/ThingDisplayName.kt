package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * The two lines that name a Thing in a list — the switcher, the export picker, anywhere a mixed
 * account is shown as rows.
 *
 * Both walk what the template declares rather than aviation spec keys. The order is PRD §9.1's:
 * the name an owner uses, then the field the template marks `title_candidate`, then make and model,
 * then its identifier.
 *
 * **The last two branches are why this is not a one-liner.** The home preset declares no
 * `title_candidate` and no `is_identifier`, and a house has no make or model, so a nameless one
 * exhausted every branch above and rendered as a blank row with a checkmark. Whatever the template
 * *does* ask for stands in, and the type's own name below that.
 */
fun Thing.displayLabel(template: ThingTemplate?): String {
  val fields = template?.spec_fields.orEmpty()
  return name
    .ifBlank {
      fields.firstOrNull { it.title_candidate }
        ?.let { specValue(it.key) }
        .orEmpty()
    }
    .ifBlank { makeAndModel() }
    .ifBlank { firstIdentifier(template) }
    .ifBlank {
      fields.firstNotNullOfOrNull { f -> specValue(f.key).takeIf { it.isNotBlank() } }
        .orEmpty()
    }
    .ifBlank { template?.display_name.orEmpty() }
}

/**
 * The second line, which never repeats [displayLabel].
 *
 * A row saying the same thing twice reads as a bug rather than as detail, and the label above is
 * usually already the make and model or the identifier.
 */
fun Thing.displaySubtitle(template: ThingTemplate?): String {
  val label = displayLabel(template)
  val makeModel = makeAndModel()
  if (makeModel.isNotBlank() && makeModel != label) return makeModel
  return firstIdentifier(template).takeIf { it != label }
    .orEmpty()
}

/**
 * The first identifier the Thing has filled in, not the first the template declares. A car declares
 * its plate before its VIN, and one with no plate would otherwise read as having no identifier.
 */
private fun Thing.firstIdentifier(template: ThingTemplate?): String =
  template?.spec_fields.orEmpty()
    .filter { it.is_identifier }
    .firstNotNullOfOrNull { f -> specValue(f.key).takeIf { it.isNotBlank() } }
    .orEmpty()

/** "Tesla Model Y" — also what [ThingInflater] names a Thing by when no title candidate is filled. */
internal fun Thing.makeAndModel(): String =
  listOf(specValue(SpecKeys.MAKE), specValue(SpecKeys.MODEL)).joinAsPhrase()
