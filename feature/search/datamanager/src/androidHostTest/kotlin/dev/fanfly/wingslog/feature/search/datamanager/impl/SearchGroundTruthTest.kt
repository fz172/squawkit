package dev.fanfly.wingslog.feature.search.datamanager.impl

import com.google.common.truth.Truth.assertWithMessage
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate
import org.junit.Test

/** The regression guard for every later synonym or weight change: expected record in the top 5. */
class SearchGroundTruthTest {

  private data class Record(
    val id: String,
    val title: String,
    val body: String = "",
    val ref: String = ""
  )

  private object Adapter : RecordAdapter<Record> {
    override fun fields(item: Record) = listOf(
      SearchField("ref", item.ref, 4),
      SearchField("title", item.title, 3),
      SearchField("body", item.body, 1),
    )

    override fun component(item: Record) = ComponentType.COMPONENT_UNKNOWN
    override fun date(item: Record): LocalDate? = null
  }

  private val logbook = listOf(
    Record(
      "gasket",
      "Replaced left magneto base gasket",
      "Torqued to spec, ground run leak check satisfactory."
    ),
    Record(
      "xpdr",
      "Installed Garmin GTX 335",
      "Removed KT-76A transponder, serial 3AB012345. Tested per 91.413 and 91.217."
    ),
    Record(
      "oil",
      "Oil and filter change",
      "Phillips X/C 20W-50, 7 qt. Filter cut open, no metal found."
    ),
    Record(
      "strut",
      "Serviced nose strut",
      "MIL-H-5606 and nitrogen to 45 psi."
    ),
    Record(
      "elt",
      "ELT battery",
      "Replaced emergency locator transmitter battery pack, ACK E-04. Self-test satisfactory."
    ),
    Record(
      "annual",
      "Annual inspection",
      "Completed IAW Cessna 172S MM. Aircraft returned to service.",
      ref = "AD 2011-10-09"
    ),
    Record(
      "alternator",
      "Alternator field wire repair",
      "Chafed at firewall pass-through. Repaired and secured."
    ),
    Record(
      "spinner",
      "Spinner screw",
      "Replaced missing prop spinner bulkhead screw and safetied."
    ),
    Record(
      "certs",
      "IFR certification",
      "Transponder and altimeter certification per 91.411 and 91.413.",
      ref = "91.413"
    ),
    Record("xpdrinop", "XPDR inop", "Mode C drops out on climb."),
    Record("mags", "Mag check rough", "Right mag drop 200 rpm at runup."),
    Record("brakes", "Brake pads", "Replaced left and right brake linings."),
  )

  private val cases = listOf(
    // typos
    "trasnponder" to "xpdr",
    "magnto" to "gasket",
    "alternater" to "alternator",
    "spiner" to "spinner",
    // acronyms and synonyms, both directions
    "xpdr" to "xpdr",
    "transponder" to "xpdrinop",
    "elt" to "elt",
    "emergency locator" to "elt",
    "mags" to "gasket",
    "magneto" to "mags",
    "prop spinner" to "spinner",
    "inop" to "xpdrinop",
    "exam" to "mags",
    "inspect" to "annual",
    "checked" to "annual",
    // exact serials and references
    "3AB012345" to "xpdr",
    "91.413" to "certs",
    "AD 2011-10-09" to "annual",
    "2011-10-09" to "annual",
    // plurals and phrases
    "brake" to "brakes",
    "oil change" to "oil",
    "nose strut" to "strut",
  )

  @Test
  fun expectedRecordIsInTheTopFive() {
    val engine = SearchEngineImpl()
    val failures = cases.mapNotNull { (query, expected) ->
      val top = engine.search(
        logbook,
        Adapter,
        RecordFilter(query = query),
        LocalDate(2026, 9, 6)
      )
        .take(5)
        .map { it.item.id }
      if (expected !in top) "“$query” → expected $expected, got $top" else null
    }
    assertWithMessage(failures.joinToString("\n")).that(failures)
      .isEmpty()
  }

  @Test
  fun exactReferenceOutranksBodyMention() {
    val engine = SearchEngineImpl()
    val top = engine.search(
      logbook,
      Adapter,
      RecordFilter(query = "91.413"),
      LocalDate(2026, 9, 6)
    )
      .map { it.item.id }
    assertWithMessage(top.toString()).that(top.first())
      .isEqualTo("certs")
  }
}
