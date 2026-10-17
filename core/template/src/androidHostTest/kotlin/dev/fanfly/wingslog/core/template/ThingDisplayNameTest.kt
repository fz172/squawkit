package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * The two lines that name a Thing in the switcher, read the way the switcher gets them: the Thing
 * written through [ThingInflater] first, since that is where a stored name comes from.
 */
class ThingDisplayNameTest {

  private fun written(template: ThingTemplate, vararg spec: Pair<String, String>): Thing =
    ThingInflater.inflate(
      Thing(id = "t", spec = spec.map { (key, value) -> Spec(key = key, value_ = value) }),
      template,
    )

  private fun Thing.rows(template: ThingTemplate): List<String> =
    listOf(displayLabel(template), displaySubtitle(template))

  @Test
  fun aCarReadsLikeAnAirplanePlateAboveMakeAndModel() {
    val airplane = AirplaneTemplate.TEMPLATE
    val plane = written(
      airplane,
      SpecKeys.MAKE to "Sling",
      SpecKeys.MODEL to "TSi",
      SpecKeys.TAIL_NUMBER to "N532SL",
    )
    val automotive = CanonicalTemplates.AUTOMOTIVE
    val car = written(
      automotive,
      SpecKeys.MAKE to "Tesla",
      SpecKeys.MODEL to "Model Y",
      "vin" to "7SAYGDEE1NF000001",
      "license_plate" to "8XYZ123",
    )

    assertThat(plane.rows(airplane)).containsExactly("N532SL", "Sling TSi")
      .inOrder()
    // The VIN was the second line before the plate existed; the make and model are now.
    assertThat(car.rows(automotive)).containsExactly("8XYZ123", "Tesla Model Y")
      .inOrder()
  }

  @Test
  fun aBoatReadsLikeAnAirplaneRegistrationAboveMakeAndModel() {
    val boatTemplate = CanonicalTemplates.BOAT
    val boat = written(
      boatTemplate,
      SpecKeys.MAKE to "Beneteau",
      SpecKeys.MODEL to "Oceanis 38",
      "hull_id" to "BEYA1234D616",
      "registration" to "CF 1234 AB",
    )
    val unregistered = written(
      boatTemplate,
      SpecKeys.MAKE to "Beneteau",
      SpecKeys.MODEL to "Oceanis 38",
      "hull_id" to "BEYA1234D616",
    )

    assertThat(boat.rows(boatTemplate)).containsExactly("CF 1234 AB", "Beneteau Oceanis 38")
      .inOrder()
    // No registration, so the Hull ID is the identifier left to show.
    assertThat(unregistered.rows(boatTemplate))
      .containsExactly("Beneteau Oceanis 38", "BEYA1234D616")
      .inOrder()
  }

  @Test
  fun aCarWithNoPlateFallsBackToMakeAndModelOverItsVin() {
    val automotive = CanonicalTemplates.AUTOMOTIVE
    val car = written(
      automotive,
      SpecKeys.MAKE to "Triumph",
      SpecKeys.MODEL to "Bonneville T100",
      "vin" to "SMTD44GN0LT000001",
    )

    assertThat(car.rows(automotive)).containsExactly("Triumph Bonneville T100", "SMTD44GN0LT000001")
      .inOrder()
  }
}
