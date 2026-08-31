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
 * Airplane component paths. The operations they feed are generic and live in `core:template`.
 *
 * #729 and #739 build paths from the template's own slots and delete this file.
 *
 * `make`/`model`/`serial` are stored twice — as `spec` and on the airframe. `spec` is the
 * authority (every reader uses it); the airframe copy is kept in step. #729 resolves that.
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

/** A new engine, with a propeller and one blade — what the form has always created. */
internal fun newEngine(): Component = Component(
  slot_key = SlotKeys.ENGINE,
  children = listOf(
    Component(
      slot_key = SlotKeys.PROPELLER,
      children = listOf(Component(slot_key = SlotKeys.BLADE)),
    ),
  ),
)

/** Writes both places identity lives. Tail number is spec-only — it belongs to the Thing. */
internal fun Thing.setIdentity(key: String, value: String): Thing {
  val updated = withSpec(key, value)
  return when (key) {
    SpecKeys.MAKE -> updated.updateComponentAt(AIRFRAME) { it.copy(make = value) }
    SpecKeys.MODEL -> updated.updateComponentAt(AIRFRAME) { it.copy(model = value) }
    SpecKeys.SERIAL -> updated.updateComponentAt(AIRFRAME) { it.copy(serial = value) }
    else -> updated
  }
}
