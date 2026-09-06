package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing

/** Any run of spaces, tabs or newlines — what a user's stray keystroke leaves between words. */
private val WHITESPACE_RUN = Regex("\\s+")

/**
 * The value with its edges trimmed and every internal run of whitespace collapsed to one space.
 *
 * A form field takes whatever is typed into it, and a trailing space is invisible in one: nothing
 * about "Sling " on screen says it is not "Sling". It only shows up once the value is joined to
 * another — "Sling  TSi" — where the double gap reads as a layout fault rather than as a typo.
 */
fun String.collapseWhitespace(): String = trim().replace(WHITESPACE_RUN, " ")

/**
 * Joins the parts into one phrase — "Sling TSi", "Airmaster AP430" — one space between each.
 *
 * Blanks are dropped rather than joined, so a Thing carrying only a make renders it alone instead
 * of trailed by a space, and each part is [collapseWhitespace]d so a value stored before saves
 * were normalised still reads right.
 */
fun List<String>.joinAsPhrase(): String =
  map { it.collapseWhitespace() }
    .filter { it.isNotEmpty() }
    .joinToString(" ")

/**
 * The Thing with every user-typed string whitespace-normalised, for the write path.
 *
 * Display-side joins normalise too, because they have to render what is already stored — but a
 * value only *read* clean is still typed back into the form the way it was saved, exported with
 * its stray space, and matched against by whatever compares two of them. Cleaning it once on the
 * way in is what keeps those honest.
 *
 * Spec labels are the user's own word for a field they invented, and a label of `" "` is load
 * bearing — `withCustomSpec` keeps a row alive while either half is filled, which is what lets a
 * just-added field survive until it is typed into. So labels are left exactly as they are.
 */
fun Thing.withNormalisedText(): Thing =
  copy(
    name = name.collapseWhitespace(),
    spec = spec.map { it.normalised() },
    components = components.map { it.normalised() },
  )

private fun Spec.normalised(): Spec = copy(value_ = value_.collapseWhitespace())

private fun Component.normalised(): Component =
  copy(
    label = label.collapseWhitespace(),
    make = make.collapseWhitespace(),
    model = model.collapseWhitespace(),
    serial = serial.collapseWhitespace(),
    spec = spec.map { it.normalised() },
    children = children.map { it.normalised() },
  )
