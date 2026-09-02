package dev.fanfly.wingslog.core.template.canonical

import dev.fanfly.wingslog.thing.ThingTemplate
import okio.ByteString.Companion.decodeBase64

/**
 * Every preset baked into this build, decoded from the assets compiled out of the text protos in `templates/`.
 *
 * The presets of PRD §4 plus airplane, with car and motorcycle merged into `automotive`. Two of them carry more weight than the rest and are worth knowing
 * about before changing anything here:
 *
 * **`home` is the load-bearing one** (`pivot_rollout_design.md` §5). No make, no model, no serial,
 * no component slots, and an empty meter list — it is the preset that finds screens with an
 * aviation assumption baked in. **`custom` is the floor**: it declares almost nothing, so a screen
 * that breaks on it is reading something no template promises.
 *
 * Ordered by `sort_order` at the point of use, not here — [ALL] is declaration order, and the
 * picker's order is the template's own business.
 */
object CanonicalTemplates {

  private fun decode(base64: String, id: String): ThingTemplate =
    ThingTemplate.ADAPTER.decode(
      checkNotNull(base64.decodeBase64()) { "$id asset is not valid base64" },
    )

  val HOME: ThingTemplate = decode(HOME_BASE64, "home")
  /** Cars and motorcycles both — see the text proto for why they are one preset, not two. */
  val AUTOMOTIVE: ThingTemplate = decode(AUTOMOTIVE_BASE64, "automotive")
  val BIKE: ThingTemplate = decode(BIKE_BASE64, "bike")
  val BOAT: ThingTemplate = decode(BOAT_BASE64, "boat")
  val CUSTOM: ThingTemplate = decode(CUSTOM_BASE64, "custom")

  /**
   * The pool, airplane first because it is the only one with Things in the field today.
   *
   * Adding to this list is what makes a preset real — `TemplateKeysResolveTest` and
   * `CanonicalTemplatesTest` both run over it, so a preset registered here is validated without
   * anyone remembering to extend a test.
   */
  val ALL: List<ThingTemplate> = listOf(
    AirplaneTemplate.TEMPLATE,
    AUTOMOTIVE,
    BIKE,
    BOAT,
    HOME,
    CUSTOM,
  )
}
