package dev.fanfly.wingslog.feature.logs.datamanager.authorship

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Technician
import org.junit.Test

private const val TECH_UID = "uid-technician"
private const val OWNER_UID = "uid-owner"

class LogAuthorshipTest {

  private val names = mapOf(TECH_UID to "Sponge Bob", OWNER_UID to "Fan Zhang")
  private fun nameFor(uid: String): String? = names[uid]

  private fun log(technician: Technician?) =
    MaintenanceLog(id = "log-1", work_description = "Oil change", technician = technician)

  /** A technician picked from a first-party profile carries that account's uid. */
  private fun linked(uid: String, name: String) =
    Technician(id = uid, name = name, source_uid = uid)

  @Test
  fun theTechnicianWroteItThemselves_isSelfSigned() {
    val result = log(linked(TECH_UID, "Sponge Bob"))
      .authorship(writerUid = TECH_UID, nameForUid = ::nameFor)

    assertThat(result).isEqualTo(LogAuthorship.SelfSigned("Sponge Bob"))
  }

  @Test
  fun someoneElseNamedThemAsTechnician_isAssigned() {
    // The owner wrote the row and attributed the work to the technician. Snapshot data alone can't
    // tell this apart from the technician signing — the envelope's writer_uid can, and it is
    // rules-enforced, so it cannot be forged.
    val result = log(linked(TECH_UID, "Sponge Bob"))
      .authorship(writerUid = OWNER_UID, nameForUid = ::nameFor)

    assertThat(result).isEqualTo(
      LogAuthorship.Assigned(authorName = "Fan Zhang", technicianName = "Sponge Bob")
    )
  }

  @Test
  fun anAssigningAuthorWeCannotName_isStillReportedAsAssigned() {
    // A member who published no mirror has no name we can show — but the fact that someone else
    // wrote it is the part that matters, and we still say so.
    val result = log(linked(TECH_UID, "Sponge Bob"))
      .authorship(writerUid = "uid-nobody-we-know", nameForUid = ::nameFor)

    assertThat(result).isEqualTo(
      LogAuthorship.Assigned(authorName = null, technicianName = "Sponge Bob")
    )
  }

  // ---- the cases that must stay silent ----

  @Test
  fun aHandTypedTechnician_isUnverifiable_notAssigned() {
    // No source_uid: the technician was typed in by hand and belongs to no account, so nothing can
    // attest the name — anyone can type anything. That is worth saying. What it is NOT is
    // "assigned": reporting that would accuse the author of something the data does not show.
    val manual = Technician(id = "m1", name = "Hand-typed Mechanic")

    val result = log(manual).authorship(writerUid = OWNER_UID, nameForUid = ::nameFor)

    assertThat(result).isEqualTo(LogAuthorship.Unverifiable("Hand-typed Mechanic"))
  }

  @Test
  fun aHandTypedTechnician_isUnverifiable_evenWhenTheyTypedItThemselves() {
    // The writer being the only account in play changes nothing: a typed name is still just text,
    // with no account behind it to check it against.
    val manual = Technician(id = "m1", name = "Hand-typed Mechanic")

    val result = log(manual).authorship(writerUid = TECH_UID, nameForUid = ::nameFor)

    assertThat(result).isEqualTo(LogAuthorship.Unverifiable("Hand-typed Mechanic"))
  }

  @Test
  fun aRevisionPredatingAttestation_isUnknown_notAssigned() {
    // writer_uid is null on rows written before the field existed. Absence of proof is not proof of
    // third-party entry.
    val result = log(linked(TECH_UID, "Sponge Bob"))
      .authorship(writerUid = null, nameForUid = ::nameFor)

    assertThat(result).isEqualTo(LogAuthorship.Unknown)
  }

  @Test
  fun aLogWithNoTechnician_isUnknown() {
    assertThat(log(null).authorship(writerUid = OWNER_UID, nameForUid = ::nameFor))
      .isEqualTo(LogAuthorship.Unknown)
  }

  @Test
  fun aTechnicianWithABlankName_isUnknown() {
    val blank = Technician(id = TECH_UID, name = "  ", source_uid = TECH_UID)

    assertThat(blank.let { log(it) }.authorship(writerUid = TECH_UID, nameForUid = ::nameFor))
      .isEqualTo(LogAuthorship.Unknown)
  }
}
