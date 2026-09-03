package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * The Thing's own spec, split into a headline and its labelled lines.
 *
 * The bug this replaces: an airplane declares two identifiers, the card captioned one line "S/N"
 * and ran everything else together, so a tail number was shown as a serial and the serial rode
 * along in the make/model run as if it were part of the model name.
 *
 * Every canonical preset is walked here, because the shape of the block is different for each and
 * only one of them is an aeroplane: two identifiers, one, or none; a headline, or nothing to head
 * with. `home` is the load-bearing case and `custom` the floor.
 */
class ThingSpecLinesTest {

  private val airplane = AirplaneTemplate.TEMPLATE

  private fun thing(vararg spec: Pair<String, String>) = Thing(
    id = "t",
    spec = spec.map { (key, value) -> Spec(key = key, value_ = value) },
  )

  /** "Tail Number: N532SL" — how the block reads, in one string per line. */
  private fun ThingSpecLines.rendered(): List<String> =
    listOfNotNull(headline.takeIf { it.isNotBlank() }) +
      lines.map { "${it.label}: ${it.value}" }

  @Test
  fun anAirplaneShowsBothIdentifiersUnderTheirOwnWords() {
    val spec = airplane.specLines(
      thing(
        SpecKeys.MAKE to "Sling",
        SpecKeys.MODEL to "TSi",
        SpecKeys.SERIAL to "532SK",
        SpecKeys.TAIL_NUMBER to "N532SL",
      ),
    )

    // Make and model name what it is; neither identifier is allowed into that run, and the tail
    // number leads the serial because it is what an owner calls the aeroplane by.
    assertThat(spec.rendered()).containsExactly(
      "Sling TSi",
      "Tail Number: N532SL",
      "Serial Number: 532SK",
    )
      .inOrder()
    // Both identifiers render in mono; there is nothing else on the block to render otherwise.
    assertThat(spec.lines.map { it.isIdentifier }).containsExactly(true, true)
    // The hero above the card shows this one big and unlabelled — the tail number, never the
    // serial, because the template marks it title_candidate rather than declaring it first.
    assertThat(spec.title).isEqualTo("N532SL")
  }

  @Test
  fun anUnfilledFieldIsDroppedRatherThanLabelled() {
    val spec = airplane.specLines(
      thing(SpecKeys.MAKE to "Sling", SpecKeys.TAIL_NUMBER to "N532SL"),
    )

    // "Serial Number:" with nothing after it reads as a load that failed, not as a blank field.
    assertThat(spec.rendered()).containsExactly("Sling", "Tail Number: N532SL")
      .inOrder()
  }

  @Test
  fun aCarSaysVinAndKeepsItsYearOnItsOwnLine() {
    val spec = CanonicalTemplates.AUTOMOTIVE.specLines(
      thing(
        SpecKeys.MAKE to "Honda",
        SpecKeys.MODEL to "Civic",
        "year" to "2019",
        "vin" to "1HGBH41JXMN109186",
      ),
    )

    // Not "S/N" — the label is the template's, so a car says VIN without this code knowing a VIN
    // exists. The year is a datum, not part of the product's name, so it takes a labelled line.
    assertThat(spec.rendered()).containsExactly(
      "Honda Civic",
      "Year: 2019",
      "VIN: 1HGBH41JXMN109186",
    )
      .inOrder()
    // Only the VIN is matched exactly, so only the VIN renders in mono.
    assertThat(spec.lines.filter { it.isIdentifier }.map { it.label }).containsExactly("VIN")
    // No title_candidate, so the hero shows the make and model alone rather than a 17-character
    // VIN set in display type.
    assertThat(spec.title).isEmpty()
  }

  @Test
  fun aBikeAndABoatSayTheirOwnIdentifier() {
    val bike = CanonicalTemplates.BIKE.specLines(
      thing(SpecKeys.MAKE to "Trek", SpecKeys.MODEL to "Domane", "frame_number" to "WTU123K0001Z"),
    )
    assertThat(bike.rendered()).containsExactly(
      "Trek Domane",
      "Frame Number: WTU123K0001Z",
    )
      .inOrder()

    val boat = CanonicalTemplates.BOAT.specLines(
      thing(
        SpecKeys.MAKE to "Beneteau",
        SpecKeys.MODEL to "Oceanis 40",
        "year" to "2016",
        "hull_id" to "ABC12345D616",
      ),
    )
    assertThat(boat.rendered()).containsExactly(
      "Beneteau Oceanis 40",
      "Year: 2016",
      "Hull ID: ABC12345D616",
    )
      .inOrder()
  }

  @Test
  fun aHomeReadsAsLabelledFactsWithNoHeadlineAndNoIdentifier() {
    // The load-bearing case: a home has no make, no model and no serial. Its address is a datum
    // shown beside its label, not a product name — running it into the year built would print
    // "742 Evergreen Terrace 1974", which reads as one mangled value rather than two facts.
    val spec = CanonicalTemplates.HOME.specLines(
      thing("address" to "742 Evergreen Terrace", "year_built" to "1974"),
    )

    assertThat(spec.headline).isEmpty()
    assertThat(spec.rendered()).containsExactly(
      "Address: 742 Evergreen Terrace",
      "Year Built: 1974",
    )
      .inOrder()
    // No identifier, so nothing on a home renders in mono, and the block is still worth drawing.
    assertThat(spec.lines.none { it.isIdentifier }).isTrue()
    assertThat(spec.isEmpty).isFalse()
    // Nothing for the hero either, which is what makes it fall back to the Thing's own name.
    assertThat(spec.title).isEmpty()
  }

  @Test
  fun aTemplateDeclaringNoSpecFieldsYieldsNothingToDraw() {
    // `custom` is the floor: a Thing with a name and nothing else the template asked for. A value
    // stored under a key it never declared stays out of the block rather than appearing unlabelled.
    assertThat(CanonicalTemplates.CUSTOM.specLines(thing(SpecKeys.MAKE to "Sling")).isEmpty)
      .isTrue()
    // The account-level screen, where nothing is selected and there is no template at all.
    val nothingSelected: ThingTemplate? = null
    assertThat(nothingSelected.specLines(thing(SpecKeys.MAKE to "Sling")).isEmpty).isTrue()
  }

  @Test
  fun everyPresetRendersEveryFieldItDeclaresExactlyOnce() {
    // The guard against a preset added later falling through the split: whatever a template
    // declares and a Thing fills in has to come out somewhere the user can read it.
    //
    // Three surfaces now, not two. `title` joined them when `custom` arrived: its name IS the
    // hero, so the row was dropped to stop the card repeating it, and a two-surface check read
    // that as a field falling through.
    CanonicalTemplates.ALL.forEach { template ->
      val values = template.spec_fields.associate { it.key to "v-${it.key}" }
      val spec = template.specLines(
        Thing(id = "t", spec = values.map { (key, value) -> Spec(key = key, value_ = value) }),
      )
      val headline = spec.headline.split(" ")
        .filter { it.isNotBlank() }
      val lines = spec.lines.map { it.value }

      // The headline and the lines stay a partition — a value in both is the duplication the
      // split exists to prevent.
      assertThat(headline.intersect(lines.toSet())).isEmpty()
      // `title` may legitimately repeat a line: an airplane shows its tail number in the hero
      // and labels it in the card, because there it sits beside a serial.
      assertThat((headline + lines + spec.title).filter { it.isNotBlank() }.toSet())
        .containsExactlyElementsIn(values.values)
    }
  }

  @Test
  fun theHeroTitleIsNotRepeatedAsARowBeneathIt() {
    // custom's only declared field IS the name, and with no make and model the hero renders it —
    // so the card would print the same string two lines below its own heading.
    val custom = CanonicalTemplates.CUSTOM
    val thing = Thing(spec = listOf(Spec(key = "name", value_ = "Espresso Machine")))

    val lines = custom.specLines(thing)

    assertThat(lines.title).isEqualTo("Espresso Machine")
    assertThat(lines.headline).isEmpty()
    assertThat(lines.lines).isEmpty()
  }

  @Test
  fun anIdentifierUnderAHeadlineKeepsItsRow() {
    // The airplane case the rule must not break: the hero shows "Sling TSi", and the row says
    // which of the two identifiers the tail number is.
    val airplane = AirplaneTemplate.TEMPLATE
    val thing = Thing(
      spec = listOf(
        Spec(key = "make", value_ = "Sling"),
        Spec(key = "model", value_ = "TSi"),
        Spec(key = "tail_number", value_ = "N532SL"),
      ),
    )

    val lines = airplane.specLines(thing)

    assertThat(lines.headline).isEqualTo("Sling TSi")
    assertThat(lines.lines.map { it.value }).contains("N532SL")
  }
}
