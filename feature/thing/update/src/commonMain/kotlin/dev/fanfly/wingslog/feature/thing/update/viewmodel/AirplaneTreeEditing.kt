package dev.fanfly.wingslog.feature.thing.update.viewmodel

import dev.fanfly.wingslog.core.template.ComponentPath
import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.childrenInSlot
import dev.fanfly.wingslog.core.template.rootComponentInSlot
import dev.fanfly.wingslog.core.template.updateComponentAt
import dev.fanfly.wingslog.core.template.withSpec
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Thing

/**
 * Where an airplane's parts sit in the component tree — **paths, not operations**.
 *
 * The operations are generic and live in `core:template`: [updateComponentAt] and friends take a
 * path and know nothing about aviation. This file supplies the paths, which is the only part that
 * *is* airplane-specific, and it is small because that is all the domain knowledge amounts to.
 *
 * That split matters for what comes next. #729 renders a component tree whose shape comes from the
 * template, and #739 builds a create form from the template's own slots — both build paths from
 * `ThingTemplate.component_slots` instead of hardcoding them here, and neither has to replace the
 * editing primitives to do it. This file is what they delete; `core:template` is what they keep.
 *
 * ## Where a value lives
 *
 * `make`, `model` and `serial` are stored **twice** in an inflated Thing: as `spec` entries and on
 * the `airframe` component. Both were derived from the same legacy fields by the cutover, so they
 * have always agreed, and nothing had to decide which was authoritative until the form began
 * writing them directly.
 *
 * **`spec` is the authority** — it is what every reader moved to in part 1, and it is the
 * template-shaped one. The airframe's copy is kept in step so the stored document does not
 * contradict itself, but no reader depends on it. Removing that redundancy belongs with #729.
 */

private val AIRFRAME: ComponentPath = listOf(SlotKeys.AIRFRAME to 0)

internal fun enginePath(engineIndex: Int): ComponentPath =
  AIRFRAME + (SlotKeys.ENGINE to engineIndex)

internal fun propellerPath(engineIndex: Int): ComponentPath =
  enginePath(engineIndex) + (SlotKeys.PROPELLER to 0)

internal fun hubPath(engineIndex: Int): ComponentPath =
  propellerPath(engineIndex) + (SlotKeys.HUB to 0)

internal fun bladePath(engineIndex: Int, bladeIndex: Int): ComponentPath =
  propellerPath(engineIndex) + (SlotKeys.BLADE to bladeIndex)

/** The engines, in order. Empty for a Thing with no tree yet, or a glider. */
internal val Thing.engines: List<Component>
  get() = rootComponentInSlot(SlotKeys.AIRFRAME)?.childrenInSlot(SlotKeys.ENGINE).orEmpty()

/**
 * A new engine, with a propeller and one blade.
 *
 * Not an empty engine: the form has always started one this way, so the user types into a propeller
 * and a first blade rather than adding them. Preserved deliberately — changing it would be a
 * product change riding along on a refactor.
 */
internal fun newEngine(): Component = Component(
  slot_key = SlotKeys.ENGINE,
  children = listOf(
    Component(
      slot_key = SlotKeys.PROPELLER,
      children = listOf(Component(slot_key = SlotKeys.BLADE)),
    ),
  ),
)

/**
 * Sets a Thing-level identity value in both places it is stored — see the note above.
 *
 * Only make, model and serial live on the airframe; a tail number belongs to the Thing rather than
 * to any component, so it is spec-only.
 */
internal fun Thing.setIdentity(key: String, value: String): Thing {
  val updated = withSpec(key, value)
  return when (key) {
    SpecKeys.MAKE -> updated.updateComponentAt(AIRFRAME) { it.copy(make = value) }
    SpecKeys.MODEL -> updated.updateComponentAt(AIRFRAME) { it.copy(model = value) }
    SpecKeys.SERIAL -> updated.updateComponentAt(AIRFRAME) { it.copy(serial = value) }
    else -> updated
  }
}
