package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * One stored spec value, paired with the template's own word for it.
 *
 * [label] comes from the template — "Tail Number", "VIN", "Hull ID", "Year Built" — because
 * which fields exist and what they are called is the template's decision, not this code's.
 */
data class SpecLine(
  val label: String,
  val value: String,
  /** Renders in mono: a value matched exactly, where character alignment carries meaning. */
  val isIdentifier: Boolean,
)

/**
 * A Thing's own spec, as a screen reads it: a headline naming what the thing *is*, then one
 * labelled line per remaining field.
 *
 * The identity used to be squashed onto a single "make · model · identifier" run under an S/N
 * caption, which mislabelled every identifier an airplane has: it holds two — the serial and the
 * tail number — and one caption can only name one of them, so the other rode along in the
 * make/model run as if it were part of the model name. Giving each value the word for it is what
 * makes the block right for a preset with two identifiers, one, or none.
 */
data class ThingSpecLines(
  /**
   * "Sling TSi" — make and model, the phrase that names the product itself. Empty for a preset
   * that declares neither.
   */
  val headline: String,
  val lines: List<SpecLine>,
  /**
   * "N532SL" — the value of whichever field the template marks `title_candidate`, the one an
   * owner calls the thing by. Empty for a preset that marks none: every preset but airplane.
   *
   * Also in [lines] under its label — unless there is no headline above it, in which case the
   * hero is already showing this exact string and the row would repeat it.
   */
  val title: String,
) {
  /** True when the template declared nothing, or the Thing has filled none of it in. */
  val isEmpty: Boolean get() = headline.isBlank() && lines.isEmpty()
}

/**
 * The two keys that read as a phrase rather than as data.
 *
 * Every other field is a datum shown beside its label — a year, an address, a VIN. Make and model
 * are the exception because together they name the product itself: "Sling TSi", "Honda Civic".
 * Naming them here is not an aviation assumption creeping back in — they are the conventional
 * keys [SpecKeys] already declares, they are the pair `AdaptiveShellViewModel` already composes
 * a display label from, and a preset declaring neither (home) simply has no headline.
 */
private val HEADLINE_KEYS = setOf(SpecKeys.MAKE, SpecKeys.MODEL)

/**
 * Splits [thing]'s stored spec into a headline and its labelled lines.
 *
 * Blank values are dropped rather than rendered under their label: "Tail Number:" followed by
 * nothing reads as data that failed to load rather than data that does not exist. A template
 * declaring no spec fields at all — `custom` — yields an empty result, which is a block a
 * screen omits entirely.
 *
 * Identifiers sink below the other fields and lead with the template's `title_candidate`, the
 * one an owner calls the thing by. Declared order alone would put an airplane's serial above its
 * tail number, which is not how anyone identifies an aeroplane.
 */
fun ThingTemplate?.specLines(thing: Thing): ThingSpecLines {
  val fields = this?.spec_fields.orEmpty()
  val headlineFields =
    fields.filter { !it.is_identifier && it.key in HEADLINE_KEYS }
  val headlineKeys = headlineFields.map { it.key }
    .toSet()
  val rest = fields.filterNot { it.key in headlineKeys }
  // sortedByDescending is stable, so declared order survives inside each group.
  val ordered = rest.filterNot { it.is_identifier } +
    rest.filter { it.is_identifier }
      .sortedByDescending { it.title_candidate }

  val headline = headlineFields.map { thing.specValue(it.key) }
    .joinAsPhrase()
  val title = fields.firstOrNull { it.title_candidate }
    ?.let { thing.specValue(it.key) }
    .orEmpty()
  // With no make and model above it, the hero renders the title itself — so a labelled row
  // repeating it two lines below is the same string twice. `custom` is the case: its only declared
  // field IS the name. An airplane keeps its "Tail Number" row, because there the hero is showing
  // "Sling TSi" and the row says which of two identifiers this one is.
  val titleIsTheHero = headline.isBlank() && title.isNotBlank()

  return ThingSpecLines(
    headline = headline,
    // The user's own fields last, under the words they chose. A template declares none of these,
    // so they cannot come from `fields` — but a value nobody can see is a value nobody will type.
    lines = ordered.filterNot { titleIsTheHero && it.title_candidate }
      .mapNotNull { field ->
        thing.specValue(field.key)
          .takeIf { it.isNotBlank() }
          ?.let {
            SpecLine(
              label = field.label,
              value = it,
              isIdentifier = field.is_identifier,
            )
          }
      } + thing.customSpecs()
      .filter { it.label.isNotBlank() && it.value_.isNotBlank() }
      .map {
        SpecLine(
          label = it.label,
          value = it.value_,
          isIdentifier = false
        )
      },
    title = title,
  )
}
