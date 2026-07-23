package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toWireInstant
import kotlin.time.Instant
import org.junit.Test

/**
 * TechnicianMirrorWire is a DELIBERATE subset of the Technician proto — the member's published name
 * and certificate fields, flattened so other members can read them without decoding protos out of a
 * private tree (design §7). A completeness test would be the wrong guard here: it would flag the
 * unmirrored fields by design. The right guard is a round-trip proving the mirrored fields survive
 * toMirrorWire → toTechnician, so a regression in either mapping fails the build.
 */
class TechnicianMirrorWireTest {

  @Test
  fun `the mirrored fields survive the round trip`() {
    // Whole-second instant: the mirror carries seconds only, so a sub-second value wouldn't round-trip.
    val expiration = Instant.fromEpochSeconds(1_700_000_000).toWireInstant()
    val original = Technician(
      id = "local-only-id",
      name = "Avery Park",
      certificate_type = CertificateType.CERTIFICATE_TYPE_AMT,
      cert_number = "AMT-4471",
      cert_expiration = expiration,
      cert_expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
    )

    val rehydrated = original.toMirrorWire().toTechnician(memberUid = "member-1")

    assertThat(rehydrated.name).isEqualTo("Avery Park")
    assertThat(rehydrated.certificate_type).isEqualTo(CertificateType.CERTIFICATE_TYPE_AMT)
    assertThat(rehydrated.cert_number).isEqualTo("AMT-4471")
    assertThat(rehydrated.cert_expiration).isEqualTo(expiration)
    assertThat(rehydrated.cert_expire_limit).isEqualTo(CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES)

    // Identity comes from the member uid, not the mirror: it is both the id (there is no local record
    // for someone else's profile) and the source_uid that marks the entry first-party (§7.3). The
    // local-only id never travels.
    assertThat(rehydrated.id).isEqualTo("member-1")
    assertThat(rehydrated.source_uid).isEqualTo("member-1")
  }

  @Test
  fun `a name-only mirror rehydrates to the certificate defaults`() {
    val rehydrated = Technician(name = "Sam Rivera").toMirrorWire().toTechnician(memberUid = "m2")

    assertThat(rehydrated.name).isEqualTo("Sam Rivera")
    assertThat(rehydrated.certificate_type).isEqualTo(CertificateType.CERTIFICATE_TYPE_NONE)
    assertThat(rehydrated.cert_number).isEmpty()
    assertThat(rehydrated.cert_expiration).isNull()
    assertThat(rehydrated.cert_expire_limit).isEqualTo(CertExpireLimit.CERT_EXPIRE_LIMIT_UNKNOWN)
    assertThat(rehydrated.id).isEqualTo("m2")
  }
}
