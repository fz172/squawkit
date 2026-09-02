package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * One identifier the template declares, paired with what this Thing stores under it.
 *
 * [label] is the template's own word — "Tail Number", "VIN", "Hull ID" — because which identifiers
 * exist and what they are called is the template's decision, not this code's.
 */
data class SpecIdentifier(
  val label: String,
  val value: String,
)

/**
 * A Thing's own spec, as a screen reads it: a headline naming what the thing *is*, then one
 * labelled line per identifier it carries.
 *
 * The identity used to be squashed onto a single "make · model · identifier" line under an
 * S/N caption, which mislabelled every identifier an airplane has: it holds two — the serial and
 * the tail number — and one line can only caption one of them, so the other rode along in the
 * make/model run as if it were a model name. Splitting them means each value is shown beside the
 * word for it, whichever identifiers a preset happens to declare.
 */
data class ThingSpecLines(
  /** "Sling TSi" — the non-identifier fields, in the order the template declares them. */
  val headline: String,
  val identifiers: List<SpecIdentifier>,
) {
  /** True when the template declared nothing, or the Thing has filled none of it in. */
  val isEmpty: Boolean get() = headline.isBlank() && identifiers.isEmpty()
}

/**
 * Splits [thing]'s stored spec into a headline and its labelled identifiers.
 *
 * Blank values are dropped rather than rendered under their label: "Tail Number:" followed by
 * nothing reads as data that failed to load rather than data that does not exist. A template
 * declaring no spec fields at all — `custom` — yields an empty result, which is a block a screen
 * omits entirely.
 *
 * Identifiers lead with the template's `title_candidate`, the one an owner calls the thing by, and
 * the rest follow in declared order. Declared order alone would put an airplane's serial above its
 * tail number, which is not how anyone identifies an aeroplane.
 */
fun ThingTemplate?.specLines(thing: Thing): ThingSpecLines {
  val fields = this?.spec_fields.orEmpty()
  return ThingSpecLines(
    headline = fields.filterNot { it.is_identifier }
      .map { thing.specValue(it.key) }
      .filter { it.isNotBlank() }
      .joinToString(" "),
    // sortedByDescending is stable, so declared order survives inside each group.
    identifiers = fields.filter { it.is_identifier }
      .sortedByDescending { it.title_candidate }
      .mapNotNull { field ->
        thing.specValue(field.key)
          .takeIf { it.isNotBlank() }
          ?.let { SpecIdentifier(label = field.label, value = it) }
      },
  )
}
