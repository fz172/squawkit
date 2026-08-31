package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Thing

/**
 * Fills in a Thing's `name` and `template` before it is written.
 *
 * ## What this used to do, and why it stopped
 *
 * Until #668 part 3 this derived `spec` and `components` from the transitional fields 2-6, because
 * the form edited those and nothing else wrote the new ones. The form now edits `spec` and
 * `components` directly, so deriving would **overwrite the user's edit with stale legacy data** —
 * the fields it derived from are no longer the ones being changed.
 *
 * What is left is the DNA, and a name for a Thing that has none.
 */
object ThingInflater {

  /**
   * Returns [thing] with its derived fields filled in.
   *
   * [template] is the resolved template — normally `TemplateRegistry.forThingWithFallback(thing)`.
   * It is written to `Thing.template` so the Thing carries its own DNA
   * (`template_system_design.md` §5), which is what lets a share member render it from the read
   * they already make.
   */
  fun inflate(
    thing: Thing,
    template: dev.fanfly.wingslog.thing.ThingTemplate?
  ): Thing =
    thing.copy(
      name = thing.name.ifEmpty { nameOf(thing) },
      // Written even when the Thing already has spec and components: one migrated by the cutover
      // has both but no DNA, because the cutover predates field 12. Absent DNA resolves correctly
      // (§5.3), and writing it here is what shrinks that population through ordinary use.
      template = thing.template ?: template,
    )

  /**
   * `tail_number` if it has one, else `"$make $model"`, else empty.
   *
   * Read from `spec`, which is where those values live now. PRD §9.1 fixed this order when the
   * cutover derived the name, and it is kept so a Thing's name does not change under it.
   */
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

  /**
   * A component id, derived from `(thingId, path)` and nothing else.
   *
   * The path is included so a `blade` under engine 0 and a `blade` under engine 1 do not collide.
   *
   * **Must stay identical to `componentId` in `thingPayloads.ts`.** These ids are the join key logs,
   * tasks and squawks use to point at a component; changing the derivation on one side and not the
   * other silently repoints every one of them.
   */
  fun componentId(thingId: String, path: List<String>): String =
    "$thingId:${path.joinToString(".")}"
}
