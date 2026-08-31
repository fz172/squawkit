package dev.fanfly.wingslog.core.template

import com.squareup.wire.ProtoReader
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.ThingTemplate
import okio.Buffer

/**
 * Whether this build can interpret a Thing's DNA (`template_system_design.md` §6.2).
 *
 * A template that cannot be interpreted is not an error and not an empty state — it is the user's
 * data, arriving from a device running a newer build. It renders degraded rather than hidden or
 * relabelled.
 */
sealed interface TemplateResolution {
  val template: ThingTemplate

  data class Renderable(override val template: ThingTemplate) :
    TemplateResolution

  data class Degraded(
    override val template: ThingTemplate,
    val reason: DegradedReason,
  ) : TemplateResolution
}

enum class DegradedReason {
  /** The template names a versionCode floor above this build. */
  APP_TOO_OLD,

  /** The template uses a capability enum value this build has no code for. */
  UNRECOGNISED_CAPABILITY,
}

/**
 * `Capabilities` field numbers whose type is an enum: `priorities` (7), `sections` (8),
 * `export_layout` (9).
 */
private val ENUM_FIELDS = setOf(7, 8, 9)

/**
 * True when a capability enum carries a value this build cannot name.
 *
 * **Why unknown fields are the right signal, and why the field number matters.** Wire does not fail
 * on an unrecognised enum value — it drops the value into `unknownFields` tagged with its original
 * field number and leaves the typed field at its default. A future `export_layout` therefore decodes
 * as `EXPORT_LAYOUT_UNKNOWN` and renders as if the template had asked for nothing, which is exactly
 * the silent misrender `min_app_version` alone cannot prevent (see template.proto on field 3).
 *
 * A *known* field number appearing in `unknownFields` can only mean a value that field's enum does
 * not define, because a defined one would have parsed into the typed field. Reading the number is
 * what separates that from an ordinary new field — capability 10 added in a later release lands here
 * too, and must be ignored rather than degrading every Thing on the older build.
 */
internal fun Capabilities.namesUnrecognisedEnumValue(): Boolean {
  if (unknownFields.size == 0) return false
  val reader = ProtoReader(Buffer().write(unknownFields))
  val token = reader.beginMessage()
  var found = false
  while (true) {
    val tag = reader.nextTag()
    if (tag == -1) break
    if (tag in ENUM_FIELDS) found = true
    reader.skip()
  }
  reader.endMessageAndGetUnknownFields(token)
  return found
}
