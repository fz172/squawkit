package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.ScheduleType
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.StarterTask
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * That a template this build cannot render is refused before a Thing is inflated from it (#740).
 *
 * Both failure modes are silent: `min_app_version` is author-set, and a broken template renders
 * wrong rather than not at all.
 */
class TemplateValidationTest {

  private fun valid(
    id: String = "car",
    minAppVersion: Int = 0,
  ) = ThingTemplate(
    id = id,
    display_name = "Car",
    min_app_version = minAppVersion,
    spec_fields = listOf(SpecField(key = "vin", label = "VIN")),
    component_slots = listOf(
      ComponentSlot(
        slot_key = "engine",
        label = "Engine"
      )
    ),
    meters = listOf(
      MeterDef(
        key = "odometer",
        label = "Odometer",
        component_slot_key = "engine"
      )
    ),
    capabilities = Capabilities(
      meters = true,
      schedule_types = listOf(
        ScheduleType.SCHEDULE_TYPE_CALENDAR,
        ScheduleType.SCHEDULE_TYPE_METER
      ),
    ),
  )

  @Test
  fun aWellFormedTemplateHasNoProblems() {
    assertThat(valid().structuralProblems()).isEmpty()
  }

  @Test
  fun scheduleTypesMustBeDeclaredAndBackedByWhatTheyNeed() {
    // Explicit, not defaulted: an empty list is a preset that has not said how its tasks are
    // scheduled, and METER without a meter is a form step that cannot complete.
    val undeclared = valid().copy(capabilities = Capabilities(meters = true))
    assertThat(undeclared.structuralProblems()).containsExactly("car: capabilities.schedule_types is empty")

    val meterless = valid().copy(
      capabilities = Capabilities(
        meters = false,
        schedule_types = listOf(
          ScheduleType.SCHEDULE_TYPE_CALENDAR,
          ScheduleType.SCHEDULE_TYPE_METER
        ),
      ),
    )
    assertThat(meterless.structuralProblems())
      .containsExactly("car: schedule_types lists METER but meters is off")
  }

  @Test
  fun aStarterTaskNeedsTheScheduleTypeItUses() {
    val seasonalOnACar = valid().copy(
      starter_tasks = listOf(
        StarterTask(
          title = "Wax",
          description = "Spring",
          months = listOf(4)
        )
      ),
    )
    assertThat(seasonalOnACar.structuralProblems())
      .containsExactly("car: starter task 'Wax' is seasonal but schedule_types does not list SEASONAL")
  }

  @Test
  fun aBlankKeyIsAProblemWhereverItIs() {
    val broken = valid().copy(
      spec_fields = listOf(SpecField(label = "Nameless")),
      meters = listOf(MeterDef(label = "Nameless")),
      component_slots = listOf(ComponentSlot(label = "Nameless")),
    )

    assertThat(broken.structuralProblems()).hasSize(3)
  }

  @Test
  fun twoFieldsSharingAKeyIsAProblem() {
    // Both write the same `Spec` entry: the second render wins, the first silently does nothing.
    val broken = valid().copy(
      spec_fields = listOf(
        SpecField(key = "vin", label = "VIN"),
        SpecField(key = "vin", label = "Chassis number"),
      ),
    )

    assertThat(broken.structuralProblems())
      .containsExactly("car: duplicate spec field key 'vin'")
  }

  @Test
  fun aMeterNamingASlotThatDoesNotExistIsAProblem() {
    val broken = valid().copy(
      meters = listOf(
        MeterDef(
          key = "engine_hours",
          component_slot_key = "turbine"
        )
      ),
    )

    assertThat(broken.structuralProblems())
      .containsExactly("car: meter 'engine_hours' names slot 'turbine', which is not declared")
  }

  @Test
  fun aNestedSlotIsCheckedLikeAnyOther() {
    // Slots nest, so a top-level scan would miss the duplicate and the meter would resolve.
    val broken = valid().copy(
      component_slots = listOf(
        ComponentSlot(
          slot_key = "engine",
          children = listOf(ComponentSlot(slot_key = "engine")),
        ),
      ),
    )

    assertThat(broken.structuralProblems())
      .containsExactly("car: duplicate component slot key 'engine'")
  }

  @Test
  fun aTemplateAboveThisBuildIsNotOffered() {
    val registry = BakedInTemplateRegistry(
      appVersionCode = 1400,
      templates = listOf(valid(minAppVersion = 1500)),
    )

    assertThat(registry.canonical()).isEmpty()
    assertThat(registry.canonicalById("car")).isNull()
  }

  @Test
  fun aStructurallyBrokenTemplateIsNotOffered() {
    val registry = BakedInTemplateRegistry(
      appVersionCode = 1400,
      templates = listOf(valid().copy(display_name = "")),
    )

    assertThat(registry.canonical()).isEmpty()
    assertThat(registry.canonicalById("car")).isNull()
  }

  @Test
  fun anExistingThingStillRendersFromDnaTheBuildWouldRefuse() {
    // The distinction the refusal turns on: a Thing that exists is the user's data, so its DNA
    // resolves and degrades (§6.2) rather than being taken away.
    val tooNew = valid(minAppVersion = 1500)
    val registry = BakedInTemplateRegistry(
      appVersionCode = 1400,
      templates = listOf(tooNew),
    )

    val resolution = registry.resolve(Thing(id = "t1", template = tooNew))

    assertThat(
      registry.forThingWithFallback(
        Thing(
          id = "t1",
          template = tooNew
        )
      )
    )
      .isEqualTo(tooNew)
    assertThat(resolution)
      .isEqualTo(
        TemplateResolution.Degraded(
          tooNew,
          DegradedReason.APP_TOO_OLD
        )
      )
  }
}
