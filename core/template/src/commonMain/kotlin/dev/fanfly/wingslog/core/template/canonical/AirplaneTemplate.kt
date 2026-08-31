package dev.fanfly.wingslog.core.template.canonical

import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.ThingTemplate
import okio.ByteString.Companion.decodeBase64

/**
 * The airplane preset, decoded from the canonical asset compiled out of
 * `templates/airplane.v1.textproto`.
 *
 * **The `.textproto` is the source; this file only decodes it.** Values used to be constructed here
 * in Kotlin, which was the deviation from `template_system_design.md` §4 that #675 closed. The
 * design's argument is that baked-in and fetched templates travel one decode path — and it does not
 * bite for *airplane*, which ships in every build and is never fetched. It bites for the first
 * preset existing in **both** forms: a Phase 3 template like `car`, baked into the build that
 * introduces it and fetched by clients still on the previous one. Two hand-maintained copies of the
 * same `(id, version)` is the silent per-device inconsistency §4 warns about, with nothing able to
 * detect it. One source compiled to bytes removes the second copy rather than policing it.
 *
 * The reasoning behind each value lives in the `.textproto` beside the value it explains, which is
 * where a reader deciding whether a word is right will be looking.
 */
object AirplaneTemplate {

  const val ID: String = "airplane"
  const val VERSION: Int = 1

  /**
   * Decoded once, eagerly.
   *
   * Failing at class-init is deliberate: a corrupt or absent asset is a build-integrity problem, and
   * the alternative — a null template resolved lazily at render — would surface as a blank screen
   * far from its cause. There is no recovery worth attempting, because the bytes ship inside the
   * binary.
   */
  val TEMPLATE: ThingTemplate = ThingTemplate.ADAPTER.decode(
    checkNotNull(AIRPLANE_V1_BASE64.decodeBase64()) {
      "airplane.v1 asset is not valid base64"
    },
  )

  /**
   * Wire generates message fields as nullable because proto3 cannot distinguish absent from
   * default. These are non-null in the asset, and a null here would mean the compiled `.pb` lost a
   * whole block — [AirplaneTemplateAssetTest][dev.fanfly.wingslog.core.template.AirplaneTemplateAssetTest]
   * is what catches that, so asserting it at the accessor keeps every call site free of `!!`.
   */
  val AIRPLANE_LEXICON: Lexicon
    get() = checkNotNull(TEMPLATE.lexicon) { "airplane template has no lexicon" }

  val AIRPLANE_CAPABILITIES: Capabilities
    get() = checkNotNull(TEMPLATE.capabilities) { "airplane template has no capabilities" }

  val AIRPLANE_SPEC_FIELDS: List<SpecField> get() = TEMPLATE.spec_fields

  val AIRPLANE_COMPONENT_SLOTS: List<ComponentSlot> get() = TEMPLATE.component_slots

  val AIRPLANE_METERS: List<MeterDef> get() = TEMPLATE.meters
}
