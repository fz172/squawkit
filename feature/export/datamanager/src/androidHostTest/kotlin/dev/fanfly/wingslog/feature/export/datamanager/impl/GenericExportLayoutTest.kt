package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.ThingInflater
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Test

/**
 * `EXPORT_LAYOUT_GENERIC` — the layout for everything that is not an aeroplane (#770).
 *
 * A car used to export the paper logbook: an Airframe tab, an "Engine Unknown" tab and a
 * "Propeller Unknown" tab, three of seven describing parts it does not have, with its work logs
 * filed by `ComponentType` — always `COMPONENT_UNKNOWN` outside aviation, so they landed nowhere.
 */
class GenericExportLayoutTest {

  private fun paths(thing: Thing, template: ThingTemplate, logs: List<MaintenanceLog>): List<String> =
    entries(thing, template, logs).keys.toList()

  /** The archive's per-thing directory — the root README sits beside it, not inside it. */
  private fun folder(thing: Thing, template: ThingTemplate): String =
    paths(thing, template, emptyList())
      .first { it.contains('/') }
      .substringBefore('/')

  private fun entries(
    thing: Thing,
    template: ThingTemplate,
    logs: List<MaintenanceLog> = emptyList(),
  ): Map<String, String> {
    val bundle = ThingBundle(
      thing = ThingInflater.inflate(thing, template),
      logs = logs,
      tasks = emptyList(),
      dueByTaskId = emptyMap(),
      lastCompliedByTaskId = emptyMap(),
      squawks = emptyList(),
      tasksById = emptyMap(),
      squawksById = emptyMap(),
      techniciansById = emptyMap(),
    )
    return LogbookExportArchiveBuilder(
      templateRegistry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE),
      appVersion = "SquawkIt 1.0.260519.10 (364)",
    ).buildEntries(
      request = ExportRequest(
        thingIds = listOf(bundle.thing.id),
        dateRange = ExportDateRange.AllTime,
        includeOpenSquawks = true,
      ),
      bundles = listOf(bundle),
      attachmentManifests = emptyMap(),
      generatedAt = LocalDateTime(2026, 5, 19, 14, 45),
      timeZone = TimeZone.UTC,
    ).associate { it.path to it.bytes.decodeToString() }
  }

  private fun car() = Thing(
    id = "car-1",
    spec = listOf(
      Spec(key = "make", value_ = "Honda"),
      Spec(key = "model", value_ = "Civic"),
      Spec(key = "vin", value_ = "1LGBH41JXMN491470"),
    ),
  )

  /** The home lexicon calls its log a "work log" (home.v13), so its work table is not the car's. */
  private val HOME_WORK_TABLE = "01_Work_Logs.csv"

  private fun home() = Thing(
    id = "home-1",
    name = "Lake house",
    spec = listOf(Spec(key = "address", value_ = "655 Disko Drive")),
  )

  @Test
  fun aCarGetsNoAirframeEngineOrPropellerTabs() {
    val csv = paths(car(), CanonicalTemplates.AUTOMOTIVE, emptyList())
      .filter { it.endsWith(".csv") }

    assertThat(csv.none { it.contains("Airframe") }).isTrue()
    assertThat(csv.none { it.contains("Engine") }).isTrue()
    assertThat(csv.none { it.contains("Propeller") }).isTrue()
  }

  @Test
  fun theTablesAreNamedFromTheLexicon() {
    // A car's tasks are services and its defects are issues; a home's people are people. The words
    // are the template's, so the archive a user opens is not full of aviation nouns.
    val car = paths(car(), CanonicalTemplates.AUTOMOTIVE, emptyList())
      .map { it.substringAfterLast('/') }
    val home = paths(home(), CanonicalTemplates.HOME, emptyList())
      .map { it.substringAfterLast('/') }

    assertThat(car).containsAtLeast(
      "00_Vehicle_Info.csv",
      "01_Service_Records.csv",
      "10_Services.csv",
      "11_Issues.csv",
      "20_Mechanics.csv",
    )
    assertThat(home).containsAtLeast("11_Attention_Items.csv", "20_People.csv")
  }

  @Test
  fun theWorkTableCarriesTheTemplatesMetersAndNoOthers() {
    val log = MaintenanceLog(
      id = "log-1",
      work_description = "Oil change",
      readings = listOf(MeterReading(meter_key = MeterKeys.ODOMETER, value_ = 80000.0)),
    )
    val csv = entries(car(), CanonicalTemplates.AUTOMOTIVE, listOf(log))
      .entries.first { it.key.endsWith("01_Service_Records.csv") }.value

    assertThat(csv).contains("Odometer")
    assertThat(csv).contains("80000")
    // The three aeroplane hour columns are what the logbook layout is; a car writes none of them.
    assertThat(csv).doesNotContain("Airframe Time")
    assertThat(csv).doesNotContain("Engine Time")
  }

  @Test
  fun aHomeGetsNoMeterColumnAtAll() {
    // Home declares no meters (§4.4). A "0.0 hrs" column would be exactly the failure that warns
    // about — a number that looks like data and is not.
    val log = MaintenanceLog(id = "log-1", work_description = "Gutters cleaned")
    val csv = entries(home(), CanonicalTemplates.HOME, listOf(log))
      .entries.first { it.key.endsWith(HOME_WORK_TABLE) }.value
    val header = csv.lineSequence().first()

    assertThat(header).doesNotContain("hrs")
    assertThat(header).doesNotContain("Odometer")
    assertThat(csv).contains("Gutters cleaned")
  }

  @Test
  fun everyLogAppearsRatherThanBeingFiledByComponentType() {
    // The logbook layout files rows by ComponentType, which is COMPONENT_UNKNOWN on everything
    // outside aviation — so filtering by it here would silently drop every row.
    val logs = (1..3).map {
      MaintenanceLog(id = "log-$it", work_description = "Work $it")
    }
    val csv = entries(home(), CanonicalTemplates.HOME, logs)
      .entries.first { it.key.endsWith(HOME_WORK_TABLE) }.value

    assertThat(csv).contains("Work 1")
    assertThat(csv).contains("Work 2")
    assertThat(csv).contains("Work 3")
  }

  @Test
  fun identityComesFromTheTemplatesSpecFields() {
    val csv = entries(home(), CanonicalTemplates.HOME)
      .entries.first { it.key.endsWith("00_Home_Info.csv") }.value

    assertThat(csv).contains("Address")
    assertThat(csv).contains("655 Disko Drive")
    // Not the four aviation identity rows, which on a house were four blanks and no address.
    assertThat(csv).doesNotContain("Tail Number")
    assertThat(csv).doesNotContain("Serial Number")
  }

  @Test
  fun theUsersOwnFieldsAreExportedUnderTheirOwnLabels() {
    // A value the user can type and never see again is half a feature (#781). The template
    // declares none of these, so walking spec_fields alone would drop them.
    val thing = Thing(
      id = "custom-1",
      spec = listOf(
        Spec(key = "name", value_ = "Espresso Machine"),
        Spec(key = "custom_1", value_ = "7 Grains", label = "Water Hardness"),
        // No label: nothing to print it under, so it is dropped rather than headed by a blank.
        Spec(key = "custom_2", value_ = "Orphan"),
      ),
    )

    val csv = entries(thing, CanonicalTemplates.CUSTOM)
      .entries.first { it.key.endsWith("_Info.csv") }.value

    assertThat(csv).contains("Water Hardness")
    assertThat(csv).contains("7 Grains")
    assertThat(csv).doesNotContain("Orphan")
    // One Name row, not two: custom names itself through a declared field, so the Thing's own
    // name row would print the same string again.
    assertThat(csv.lines().count { it.startsWith("Name,") }).isEqualTo(1)
  }

  @Test
  fun anAeroplaneStillGetsThePaperLogbook() {
    // The guarantee that makes the rest of this safe: the logbook renderer is untouched.
    val plane = Thing(
      id = "plane-1",
      spec = listOf(
        Spec(key = "tail_number", value_ = "N12345"),
        Spec(key = "make", value_ = "Cessna"),
        Spec(key = "model", value_ = "172"),
      ),
    )
    val csv = paths(plane, AirplaneTemplate.TEMPLATE, emptyList())
      .map { it.substringAfterLast('/') }
      .filter { it.endsWith(".csv") }

    assertThat(csv).containsExactly(
      "00_Thing_Info.csv",
      "01_Airframe.csv",
      "02_Engine_Unknown.csv",
      "03_Propeller_Unknown.csv",
      "10_Tasks.csv",
      "11_Squawks.csv",
      "20_Technicians.csv",
    )
  }

  @Test
  fun theWorkTableColumnsAreTheTemplatesWords() {
    val car = entries(car(), CanonicalTemplates.AUTOMOTIVE, listOf(MaintenanceLog(id = "l")))
      .entries.first { it.key.endsWith("01_Service_Records.csv") }.value
      .lineSequence().first()

    assertThat(car).isEqualTo(
      "Date,Odometer (mi),Work Description,Services Completed,Reference Numbers," +
        "Issues Addressed,Mechanic,Attachments"
    )
  }

  @Test
  fun aReferenceNumberColumnOnlyAppearsWhereComplianceDoes() {
    // An AD or a service bulletin. A preset with compliance off has no field that fills one, so
    // the column could only ever be empty — the same rule as the Component column.
    fun header(thing: Thing, template: ThingTemplate, table: String) =
      entries(thing, template, listOf(MaintenanceLog(id = "l")))
        .entries.first { it.key.endsWith(table) }.value.lineSequence().first()

    assertThat(header(car(), CanonicalTemplates.AUTOMOTIVE, "01_Service_Records.csv"))
      .contains("Reference Numbers")
    assertThat(header(home(), CanonicalTemplates.HOME, HOME_WORK_TABLE))
      .doesNotContain("Reference Numbers")
  }

  @Test
  fun meterHeadersKeepTheAuthoredUnitCasing() {
    // meterUnit upper-cases for value cells ("5000 MI"); a column header reads as shouting.
    val bike = entries(
      Thing(id = "b-1", name = "Commuter"),
      CanonicalTemplates.BIKE,
      listOf(MaintenanceLog(id = "l")),
    ).entries.first { it.key.endsWith("01_Service_Records.csv") }.value.lineSequence().first()

    assertThat(bike).contains("Distance (mi)")
    assertThat(bike).contains("Ride Hours (hrs)")
    assertThat(bike).doesNotContain("(MI)")
  }

  @Test
  fun theFolderIsNamedAfterTheThingNotItsGeneratedId() {
    // A car exported as "y8WPyMmKR7Pz6HyVm5L3_Kuat_X675": the folder was tail number, make and
    // model read off the aviation spec keys, so anything else fell through to the random id.
    val folder = folder(
      Thing(id = "y8WPyMmKR7Pz6HyVm5L3", name = "Kuat X675"),
      CanonicalTemplates.AUTOMOTIVE,
    )

    assertThat(folder).startsWith("Kuat_X675")
    assertThat(folder).doesNotContain("y8WPyMmKR7Pz6HyVm5L3")
  }

  @Test
  fun anAeroplaneFolderIsUnchangedAndLosesItsDoubleUnderscore() {
    val cessna = Thing(
      id = "p1",
      spec = listOf(
        Spec(key = "tail_number", value_ = "N12345"),
        Spec(key = "make", value_ = "Cessna"),
        Spec(key = "model", value_ = "172"),
      ),
    )
    assertThat(folder(cessna, AirplaneTemplate.TEMPLATE)).isEqualTo("N12345_Cessna_172")

    // A blank make used to leave "N532SL__Sling_TSi" — an empty segment between two separators.
    val noMake = Thing(
      id = "p2",
      spec = listOf(
        Spec(key = "tail_number", value_ = "N532SL"),
        Spec(key = "model", value_ = "Sling TSi"),
      ),
    )
    assertThat(folder(noMake, AirplaneTemplate.TEMPLATE)).doesNotContain("__")
  }

}
