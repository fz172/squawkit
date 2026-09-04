package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.technician.FAA_AMT
import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.CertificateType
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.Technician
import org.junit.Test
import kotlin.time.Instant

/**
 * TechnicianMirrorWire is a DELIBERATE subset of the Technician proto — the member's published name
 * and certifications, flattened so other members can read them without decoding protos out of a
 * private tree (design §7). A completeness test would be the wrong guard here: it would flag the
 * unmirrored fields by design. The right guard is a round-trip proving the mirrored fields survive
 * toMirrorWire → toTechnician, so a regression in either mapping fails the build.
 */
class TechnicianMirrorWireTest {

  @Test
  fun `the mirrored fields survive the round trip`() {
    // Whole-second instant: the mirror carries seconds only, so a sub-second value wouldn't round-trip.
    val expiration = Instant.fromEpochSeconds(1_700_000_000)
      .toWireInstant()
    val original = Technician(
      id = "local-only-id",
      name = "Avery Park",
      certifications = listOf(
        Certification(
          type = FAA_AMT,
          number = "AMT-4471",
          expiration = expiration,
          expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
        ),
        Certification(type = "ase", number = "ASE-90"),
      ),
    )

    val rehydrated = original.toMirrorWire()
      .toTechnician(memberUid = "member-1")

    assertThat(rehydrated.name).isEqualTo("Avery Park")
    // Both, in order: a member who is an A&P and an ASE mechanic is one person with two credentials,
    // and a mirror that carried only the first would silently halve them.
    assertThat(rehydrated.certifications).isEqualTo(original.certifications)

    // Identity comes from the member uid, not the mirror: it is both the id (there is no local record
    // for someone else's profile) and the source_uid that marks the entry first-party (§7.3). The
    // local-only id never travels.
    assertThat(rehydrated.id).isEqualTo("member-1")
    assertThat(rehydrated.source_uid).isEqualTo("member-1")
  }

  @Test
  fun `a pre-684 self-record publishes its single certificate as a certification`() {
    // The member's own record may still carry fields 3-7 — nothing migrates them, the read derives
    // (PRD §8.6). Publishing from the raw field would put a name-only mirror on the share and strip
    // the A&P off a mechanic every other member sees.
    val expiration = Instant.fromEpochSeconds(1_700_000_000)
      .toWireInstant()
    val legacy = Technician(
      name = "Avery Park",
      certificate_type = CertificateType.CERTIFICATE_TYPE_AMT,
      cert_number = "AMT-4471",
      cert_expiration = expiration,
      cert_expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
    )

    val rehydrated = legacy.toMirrorWire()
      .toTechnician(memberUid = "member-1")

    assertThat(rehydrated.certifications).containsExactly(
      Certification(
        type = FAA_AMT,
        number = "AMT-4471",
        expiration = expiration,
        expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
      )
    )
  }

  @Test
  fun `a mirror published by an older client still resolves`() {
    // The flat fields are a read path, not a dead one: a member who has not updated goes on writing
    // them, and their credential has to survive the trip into this build's roster.
    val fromOldClient = TechnicianMirrorWire(
      name = "Sam Rivera",
      certificateType = CertificateType.CERTIFICATE_TYPE_AMT.name,
      certNumber = "AMT-9",
      certExpireLimit = CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES.name,
    )

    val rehydrated = fromOldClient.toTechnician(memberUid = "m3")

    assertThat(rehydrated.certifications).isEmpty()
    assertThat(rehydrated.resolvedCertifications()).containsExactly(
      Certification(
        type = FAA_AMT,
        number = "AMT-9",
        expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES,
      )
    )
  }

  @Test
  fun `a name-only mirror rehydrates to no certifications`() {
    val rehydrated = Technician(name = "Sam Rivera").toMirrorWire()
      .toTechnician(memberUid = "m2")

    assertThat(rehydrated.name).isEqualTo("Sam Rivera")
    assertThat(rehydrated.certifications).isEmpty()
    assertThat(rehydrated.resolvedCertifications()).isEmpty()
    assertThat(rehydrated.id).isEqualTo("m2")
  }
}
