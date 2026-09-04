package dev.fanfly.wingslog.feature.stresstest.fixtures

import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MeterRule
import dev.fanfly.wingslog.thing.SquawkPriority
import dev.fanfly.wingslog.thing.ThingTemplate
import dev.fanfly.wingslog.thing.TimeRule

/**
 * The sample records one preset's fake data is drawn from. Each canonical template has its own
 * file in this package; the generator only ever sees this bundle.
 */
internal data class FakeDataPool(
  val tasks: List<TaskTemplate>,
  val squawks: List<SquawkTemplate>,
  val logs: List<LogTemplate>,
)

internal data class TaskTemplate(
  val title: String,
  val component: ComponentType,
  val type: ComplianceType,
  val rule: InspectionRule,
  val notes: String = "",
  val referenceNumber: String = "",
  val complianceAuthority: String = "",
  val complianceDetails: String = "",
  val isOneTime: Boolean = false,
)

/**
 * `component` defaults to unknown because that is what the squawk form writes for every preset
 * but airplane — the type enum is aviation's alone, so only the airplane pool sets it.
 */
internal data class SquawkTemplate(
  val title: String,
  val description: String,
  val priority: SquawkPriority,
  val component: ComponentType = ComponentType.COMPONENT_UNKNOWN,
)

/** [taskHints] are substrings of task titles in the same pool; a log links to every task it matches. */
internal data class LogTemplate(
  val description: String,
  val component: ComponentType = ComponentType.COMPONENT_UNKNOWN,
  val taskHints: List<String> = emptyList(),
)

internal fun meterRule(key: String, interval: Float): InspectionRule =
  InspectionRule(meter_rule = MeterRule(meter_key = key, interval = interval))

internal fun months(count: Int): InspectionRule =
  InspectionRule(time_rule = TimeRule(interval_months = count))

internal fun routine(
  title: String,
  rule: InspectionRule,
  notes: String = "",
  component: ComponentType = ComponentType.COMPONENT_UNKNOWN,
): TaskTemplate =
  TaskTemplate(title, component, ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION, rule, notes)

internal object FakeDataPools {
  /** Which pool a preset draws from. Airplane is the fallback because it is the richest. */
  fun forTemplate(template: ThingTemplate): FakeDataPool =
    when (template.id) {
      CanonicalTemplates.AUTOMOTIVE.id -> AutomotiveFixtures.POOL
      CanonicalTemplates.BIKE.id -> BikeFixtures.POOL
      CanonicalTemplates.BOAT.id -> BoatFixtures.POOL
      CanonicalTemplates.HOME.id -> HomeFixtures.POOL
      CanonicalTemplates.CUSTOM.id -> CustomFixtures.POOL
      else -> AirplaneFixtures.POOL
    }
}

/** Names and identities shared by every preset. */
internal object SampleNames {

  data class ThingSpec(
    val make: String,
    val model: String,
    val engineMake: String,
    val engineModel: String,
    val propMake: String,
    val propModel: String,
  )

  val THING_SPECS = listOf(
    ThingSpec(
      "Incom",
      "T-65B",
      "Lycoming",
      "O-320-E2D",
      "Sensenich",
      "76EM8S5-0-62"
    ),
    ThingSpec(
      "Corellian Engineering",
      "YT-1300F",
      "Continental",
      "O-470-U",
      "McCauley",
      "1C172/ATM7553"
    ),
    ThingSpec(
      "MandalMotors",
      "Kom'rk 452",
      "Lycoming",
      "O-360-A4M",
      "Sensenich",
      "74DM6S5-0-58"
    ),
    ThingSpec(
      "Kuat Systems",
      "RZ-1 A-wing",
      "Continental",
      "IO-520-BB",
      "Hartzell",
      "HC-C2YK-1BF"
    ),
    ThingSpec(
      "SoroSuub",
      "N-1 Scout",
      "Continental",
      "IO-550-N",
      "Hartzell",
      "HC-E2YR-2ALTUF"
    ),
    ThingSpec(
      "Incom",
      "Z-95-AF4",
      "Lycoming",
      "IO-360-M1A",
      "MT-Propeller",
      "MTV-6-A-200"
    ),
    ThingSpec(
      "Kuat Systems",
      "BTL-B",
      "Lycoming",
      "IO-360-A3B6D",
      "McCauley",
      "2A34C82/82NCA"
    ),
    ThingSpec(
      "Corellian Engineering",
      "G9 Rigger",
      "Lycoming",
      "O-360-A1H6",
      "Hartzell",
      "HC-C2YK-1BF"
    ),
  )

  val TECHNICIAN_NAMES = listOf(
    "Anakin Skywalker", "Han Solo", "Rey Palpatine",
    "Poe Dameron", "Ahsoka Tano", "Bodhi Rook",
    "Cassian Andor", "Hera Syndulla",
  )

  val MAKES = listOf(
    "Acme", "Corellian", "Kuat", "Sienar", "Incom", "Rendili",
  )

  val STREETS = listOf(
    "Maple Street", "Oak Avenue", "Cedar Lane", "Birch Road", "Willow Way",
  )
}
