package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing

/**
 * Fills in a Thing's `name`, `spec`, `components` and `template` before it is written.
 *
 * ## Why this exists
 *
 * `spec` and `components` were meant to be dual-written from the moment Phase 1 landed. **Only the
 * server half shipped.** `thingPayloads.ts` builds both during the backend cutover, and the client
 * has never written either — so every Thing created since the cutover carries its make, model,
 * serial and engine tree *only* in the transitional fields 2–6, and nothing else.
 *
 * That is not a cosmetic gap. Fields 2–6 cannot be removed while they are the only copy
 * (`pivot_rollout_design.md` §7), and the degraded state in `template_system_design.md` §6.2 renders
 * "raw spec values as unlabelled key/value pairs" — which for such a Thing is nothing at all.
 *
 * ## Byte-identical to the backend, deliberately
 *
 * The backfill (#718) repairs Things this inflater did not reach, and the two must agree: a Thing
 * repaired by the backfill and the same Thing inflated here have to produce the same bytes, or
 * whichever runs second rewrites the first one's work and every log's component join moves with it.
 *
 * So this mirrors `thingPayloads.ts` exactly rather than approximately — the same spec keys in the
 * same order, the same tree shape, and above all the same [componentId] derivation.
 *
 * ## Deterministic ids
 *
 * A component id is derived from `(thingId, path)` and nothing else. Random ids would give the same
 * Thing different component ids on different devices, and last-writer-wins would then silently
 * reassign every log's component — the failure PRD §9.1 calls out. Deriving them means two devices
 * inflating the same Thing independently produce identical output without coordinating.
 */
object ThingInflater {

  /**
   * Returns [thing] with its derived fields filled in, or unchanged if they already are.
   *
   * [template] is the resolved template for this Thing — normally
   * `TemplateRegistry.forThingWithFallback(thing)`. It is written to `Thing.template` so the Thing
   * carries its own DNA (`template_system_design.md` §5), which is what lets a share member render
   * it from the read they already make.
   */
  fun inflate(
    thing: Thing,
    template: dev.fanfly.wingslog.thing.ThingTemplate?
  ): Thing {
    // `components` is the truer idempotency signal than `template`: it is what this function
    // actually produces, so it cannot report "done" for work that did not happen. Same choice
    // thingPayloads.ts makes, and for the same reason.
    val alreadyInflated = thing.components.isNotEmpty()

    // Gated on the *template*, not on emptiness. Only an airplane has legacy fields 2-6 to derive a
    // tree from, and only an airplane's tree is airframe/engine/propeller-shaped. Once the picker
    // ships (#739) a car reaches this with empty components and empty legacy fields — and without
    // this guard it would be handed a lone "Airframe" component and that would be *stored*.
    // Leaving it empty is recoverable; writing the wrong tree is not, because component ids are a
    // join key.
    val isLegacyAirplane =
      template == null || template.id == AirplaneTemplate.ID

    return thing.copy(
      name = thing.name.ifEmpty { nameOf(thing) },
      // Keyed off `spec` itself rather than off `alreadyInflated`. A Thing created from a template
      // has its spec filled by the create form before it ever reaches here, and its components are
      // still empty — so deriving `alreadyInflated` from components and using it to gate spec would
      // overwrite the form's values with the empty derivation from fields 2-6.
      spec = thing.spec.ifEmpty { specOf(thing) },
      components = when {
        alreadyInflated -> thing.components
        isLegacyAirplane -> buildLegacyAirplaneComponents(thing)
        // A template-shaped tree is #739's job, in the create flow that knows the cardinality —
        // how many engines, how many blades. The template declares that `engine` is repeatable; it
        // cannot say how many this Thing has.
        else -> emptyList()
      },
      // Written even when already inflated: a Thing migrated by the cutover has components but no
      // DNA, because the cutover predates field 12. Absent DNA resolves correctly (§5.3), but
      // writing it here is what shrinks that population through ordinary use.
      template = thing.template ?: template,
    )
  }

  /** `tail_number` if it has one, else `"$make $model"`, else empty. PRD §9.1. */
  private fun nameOf(thing: Thing): String {
    if (thing.tail_number.isNotEmpty()) return thing.tail_number
    return listOf(thing.make, thing.model).filter { it.isNotEmpty() }
      .joinToString(" ")
  }

  /**
   * The four conventional spec keys, in the order `thingPayloads.ts` writes them.
   *
   * Empty values are dropped rather than stored blank — a Thing with no tail number has no
   * `tail_number` spec, not one holding "". The backend does the same, and the two must match.
   */
  private fun specOf(thing: Thing): List<Spec> =
    listOf(
      "make" to thing.make,
      "model" to thing.model,
      "serial" to thing.serial,
      "tail_number" to thing.tail_number,
    ).filter { (_, value) -> value.isNotEmpty() }
      .map { (key, specValue) -> Spec(key = key, value_ = specValue) }

  /**
   * The airplane component tree: one `airframe` carrying the Thing's own make/model/serial, one
   * `engine` per `Engine`, a `propeller` child per engine, and `hub` / `blade` children under that.
   *
   * **The shape comes from the Thing's structure, not from which fields the user filled in.** An
   * empty legacy field still produces its component, so a half-filled aircraft gets a stable
   * skeleton to hang logs off, and filling a field in later renumbers nothing.
   *
   * **This is the legacy path, and it is airplane-shaped because the data it reads is.** Only an
   * airplane has fields 2–6 to derive from: `Engine`, its `Propeller`, that propeller's hub and
   * blades. A template declares the slot *shape* generically — airplane's `ComponentSlot` tree is
   * airframe → engine (repeatable) → propeller → hub / blade (repeatable) — but it cannot supply
   * the *cardinality*: how many engines this particular aircraft has, or how many blades.
   *
   * For a legacy Thing that cardinality comes from fields 2–6, which is why this reads them. For a
   * Thing created from a template it comes from the create form, which is why that path is #739's
   * and not this one's. [inflate] refuses to run this for a non-airplane rather than producing a
   * plausible-looking wrong tree.
   */
  private fun buildLegacyAirplaneComponents(thing: Thing): List<Component> {
    val airframe = Component(
      id = componentId(thing.id, listOf("airframe", "0")),
      slot_key = "airframe",
      label = "Airframe",
      make = thing.make,
      model = thing.model,
      serial = thing.serial,
      children = thing.engine.mapIndexed { engineIndex, engine ->
        val enginePath = listOf("engine", engineIndex.toString())
        val propeller = engine.propeller
        val propellerChildren = buildList {
          if (propeller != null) {
            val propellerPath = enginePath + listOf("propeller", "0")
            propeller.hub?.let { hub ->
              add(
                Component(
                  id = componentId(
                    thing.id,
                    propellerPath + listOf("hub", "0")
                  ),
                  slot_key = "hub",
                  label = "Hub",
                  make = hub.make,
                  model = hub.model,
                  serial = hub.serial,
                )
              )
            }
            propeller.blades.forEachIndexed { bladeIndex, blade ->
              add(
                Component(
                  id = componentId(
                    thing.id,
                    propellerPath + listOf(
                      "blade",
                      bladeIndex.toString()
                    )
                  ),
                  slot_key = "blade",
                  label = "Blade ${bladeIndex + 1}",
                  make = blade.make,
                  model = blade.model,
                  serial = blade.serial,
                )
              )
            }
          }
        }

        Component(
          id = componentId(thing.id, enginePath),
          slot_key = "engine",
          label = if (thing.engine.size > 1) "Engine ${engineIndex + 1}" else "Engine",
          make = engine.make,
          model = engine.model,
          serial = engine.serial,
          children = if (propeller == null) {
            emptyList()
          } else {
            listOf(
              Component(
                id = componentId(
                  thing.id,
                  enginePath + listOf("propeller", "0")
                ),
                slot_key = "propeller",
                label = "Propeller",
                children = propellerChildren,
              )
            )
          },
        )
      },
    )

    return listOf(airframe)
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
