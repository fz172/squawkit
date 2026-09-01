package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.ExportLayout
import dev.fanfly.wingslog.thing.Section
import dev.fanfly.wingslog.thing.Thing
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.junit.Test

/** The §6.2 gate: which DNA this build will render, and which it degrades. */
class TemplateResolutionTest {

  private val thisBuild = 1400
  private val registry = BakedInTemplateRegistry(appVersionCode = thisBuild)

  private fun thingWith(template: ThingTemplateBuilder.() -> Unit = {}): Thing {
    val builder = ThingTemplateBuilder()
    builder.template()
    return Thing(id = "t", template = builder.build())
  }

  class ThingTemplateBuilder {
    var minAppVersion: Int = 0
    var capabilities: Capabilities? = AirplaneTemplate.TEMPLATE.capabilities

    fun build() = AirplaneTemplate.TEMPLATE.copy(
      min_app_version = minAppVersion,
      capabilities = capabilities,
    )
  }

  /**
   * Hand-encodes a Capabilities carrying [values] on [fieldNumber], bypassing the generated
   * builder — which is the point: there is no way to construct an enum value this build does not
   * define, so the only way to test the reader is to write the bytes a newer build would send.
   */
  private fun capabilitiesWithRawEnum(
    fieldNumber: Int,
    vararg values: Int,
  ): Capabilities {
    val buffer = Buffer()
    values.forEach { value ->
      buffer.writeByte((fieldNumber shl 3) or 0) // varint wire type
      buffer.writeByte(value)
    }
    return Capabilities.ADAPTER.decode(
      buffer.readByteArray()
        .toByteString()
    )
  }

  @Test
  fun aTemplateThisBuildUnderstandsIsRenderable() {
    val resolution = registry.resolve(thingWith { minAppVersion = thisBuild })

    assertThat(resolution).isInstanceOf(TemplateResolution.Renderable::class.java)
  }

  @Test
  fun aFloorAboveThisBuildDegrades() {
    val resolution =
      registry.resolve(thingWith { minAppVersion = thisBuild + 1 })

    assertThat(resolution).isEqualTo(
      TemplateResolution.Degraded(
        template = (resolution as TemplateResolution.Degraded).template,
        reason = DegradedReason.APP_TOO_OLD,
      ),
    )
  }

  @Test
  fun aThingWithNoDnaRendersUnderTheFallback() {
    // The legacy case, and the one that must never degrade: the fallback ships in this build.
    val resolution = registry.resolve(Thing(id = "t"))

    assertThat(resolution).isInstanceOf(TemplateResolution.Renderable::class.java)
    assertThat(resolution.template).isEqualTo(AirplaneTemplate.TEMPLATE)
  }

  @Test
  fun anUnrecognisedSectionDegradesEvenWithNoFloorSet() {
    // The case min_app_version cannot catch: the author simply forgot to raise the floor.
    val unknownSection = Section.values()
      .maxOf { it.value } + 1
    val capabilities = capabilitiesWithRawEnum(8, unknownSection)

    val resolution = registry.resolve(
      thingWith {
        minAppVersion = 0
        this.capabilities = capabilities
      },
    )

    assertThat(resolution).isInstanceOf(TemplateResolution.Degraded::class.java)
    assertThat((resolution as TemplateResolution.Degraded).reason)
      .isEqualTo(DegradedReason.UNRECOGNISED_CAPABILITY)
  }

  @Test
  fun anUnrecognisedExportLayoutDegrades() {
    // Wire decodes this to EXPORT_LAYOUT_UNKNOWN, the default — a perfect parse that renders wrong.
    val capabilities =
      capabilitiesWithRawEnum(
        9,
        ExportLayout.values()
          .maxOf { it.value } + 1)
    assertThat(capabilities.export_layout).isEqualTo(ExportLayout.EXPORT_LAYOUT_UNKNOWN)

    val resolution =
      registry.resolve(thingWith { this.capabilities = capabilities })

    assertThat((resolution as TemplateResolution.Degraded).reason)
      .isEqualTo(DegradedReason.UNRECOGNISED_CAPABILITY)
  }

  @Test
  fun knownEnumValuesAreNotMistakenForUnknownOnes() {
    val capabilities = capabilitiesWithRawEnum(
      8,
      Section.SECTION_DASHBOARD.value,
      Section.SECTION_LOGS.value,
    )

    assertThat(capabilities.sections).hasSize(2)
    assertThat(registry.resolve(thingWith { this.capabilities = capabilities }))
      .isInstanceOf(TemplateResolution.Renderable::class.java)
  }

  @Test
  fun anUnknownNonEnumFieldIsIgnored() {
    // Capabilities 10 is reserved for a later release. An older build must ignore it, not degrade
    // every Thing — this is what reading the field number buys over "unknownFields is non-empty".
    val buffer = Buffer().apply {
      writeByte((10 shl 3) or 0)
      writeByte(1)
    }
    val capabilities =
      Capabilities.ADAPTER.decode(
        buffer.readByteArray()
          .toByteString()
      )
    assertThat(capabilities.unknownFields.size).isGreaterThan(0)

    assertThat(registry.resolve(thingWith { this.capabilities = capabilities }))
      .isInstanceOf(TemplateResolution.Renderable::class.java)
  }

  @Test
  fun theShippedAirplaneTemplateRendersOnEveryBuildThatCarriesIt() {
    // min_app_version = 0, so this holds even on the oldest build in the field.
    assertThat(BakedInTemplateRegistry(appVersionCode = 1).resolve(Thing(id = "t")))
      .isInstanceOf(TemplateResolution.Renderable::class.java)
  }
}
