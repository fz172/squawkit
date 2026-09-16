package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Which spec field a recorder's `aircraft_ident` is compared against.
 *
 * The airplane template marks **two** fields `is_identifier` — the serial number and the tail
 * number — and declares the serial first. So "the first identifier field" is the serial, and a log
 * recorded on the aeroplane it belongs to raised a mismatch against its own airframe serial.
 */
class TemplateThingIdentifierLookupTest {

  private val thingId = ThingId("thing-1")

  private val airplane = Thing(
    id = "thing-1",
    name = "N222ZZ",
    spec = listOf(
      Spec(key = "make", value_ = "Fictional"),
      Spec(key = "model", value_ = "Two"),
      Spec(key = "serial", value_ = "222ZY"),
      Spec(key = "tail_number", value_ = "N222ZZ"),
    ),
  )

  private fun lookup(thing: Thing = airplane): TemplateThingIdentifierLookup {
    val fleet = mockk<FleetManager>()
    every { fleet.loadThing("thing-1") } returns flowOf(thing)
    val templates = mockk<TemplateRegistry>()
    every { templates.forThingWithFallback(any()) } returns AirplaneTemplate.TEMPLATE
    return TemplateThingIdentifierLookup(fleet, templates)
  }

  @Test
  fun theIdentifierIsTheTailNumberNotTheSerialDeclaredBeforeIt() = runTest {
    // The serial is declared first and is also `is_identifier`, so reading "the first identifier
    // field" returned 222ZY and every log from this aeroplane looked like it came from another one.
    assertThat(lookup().identifierOf(thingId)).isEqualTo("N222ZZ")
  }

  @Test
  fun theTemplateStillMarksBothFieldsSoTheOrderAloneCannotBeTrusted() {
    // A guard on the premise rather than on the code: if the airplane template ever stops marking
    // two fields, this test is what says the fix above is no longer load-bearing.
    val identifiers =
      AirplaneTemplate.AIRPLANE_SPEC_FIELDS.filter { it.is_identifier }
    assertThat(identifiers.map { it.key }).containsExactly(
      "serial",
      "tail_number"
    )
      .inOrder()
    assertThat(AirplaneTemplate.AIRPLANE_SPEC_FIELDS.filter { it.title_candidate }
                 .map { it.key })
      .containsExactly("tail_number")
  }

  @Test
  fun aThingWithNoTailNumberHasNoIdentifierRatherThanItsSerial() = runTest {
    val noTail =
      airplane.copy(spec = airplane.spec.filterNot { it.key == "tail_number" })

    assertThat(lookup(noTail).identifierOf(thingId)).isNull()
  }
}
