package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * Fills in a Thing's `name` and `template` before it is written.
 *
 * It no longer derives `spec` or `components`: the form writes those, so deriving from the
 * retired fields would overwrite the user's edit (#668).
 */
object ThingInflater {

  /** [template] is the resolved template, written as the Thing's own DNA (design §5). */
  fun inflate(
    thing: Thing,
    template: ThingTemplate?
  ): Thing =
    thing.copy(
      name = thing.name.ifEmpty { nameOf(thing) },
      // A Thing migrated by the cutover has no DNA — the cutover predates field 12.
      //
      // The lexicon is stripped before storing. It is app UI, written against the screens of a
      // particular release, so freezing a copy into user data makes every Thing a fork of the
      // app's vocabulary that no later release can correct. Readers resolve words by template id
      // instead (TemplateRegistry.lexiconFor), which is why nothing has to migrate: a Thing that
      // already carries one simply has it ignored.
      template = (thing.template ?: template)?.copy(lexicon = null),
    )

  /** `tail_number`, else `"$make $model"`, else empty — PRD §9.1's order, read from spec. */
  private fun nameOf(thing: Thing): String {
    val tail = thing.specValue(SpecKeys.TAIL_NUMBER)
    if (tail.isNotEmpty()) return tail
    return listOf(
      thing.specValue(SpecKeys.MAKE),
      thing.specValue(SpecKeys.MODEL)
    )
      .filter { it.isNotEmpty() }
      .joinToString(" ")
  }

  /** `"$thingId:$path"`. Must match `thingPayloads.ts` — these ids are a stored join key. */
  fun componentId(thingId: String, path: List<String>): String =
    "$thingId:${path.joinToString(".")}"
}
