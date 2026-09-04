package dev.fanfly.wingslog.core.model.technician

import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.CertificateType
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.Technician

/** The `Certification.type` an airplane's `faa_repairman` is stored under. */
const val FAA_REPAIRMAN = "faa_repairman"

/** The `Certification.type` an airplane's `faa_amt` is stored under. */
const val FAA_AMT = "faa_amt"

/**
 * The person's certifications, reading the pre-#684 single-certificate fields when there are none.
 *
 * **The backfill is derivable rather than stored** (PRD §8.6). A record carrying fields 3-7 was
 * written when airplane was the only preset, so the set is closed and the aviation key is implied —
 * the same argument that let `Thing.template_id` be dropped instead of kept as a hint. Deriving it
 * on read means no migration, and no window where a device on the old build and one on the new
 * disagree about what a record says.
 *
 * Every reader goes through here. Reading `certifications` directly is what makes a legacy row look
 * uncertified — which is not a blank field but a wrong statement: it would strip the A&P off the
 * mechanic who signs the annual.
 */
fun Technician.resolvedCertifications(): List<Certification> {
  if (certifications.isNotEmpty()) return certifications
  val type = legacyCertificationKey()
  // A number or an expiry with no type still becomes a certification, under an EMPTY key. The old
  // form only let a number be typed once a type was picked, so this ought to be unreachable — but
  // "ought to be" is not a reason to drop a certificate number somebody entered, and an empty key
  // is exactly what the model already says about a credential nothing names: untagged, shown as
  // the user left it.
  if (type == null && cert_number.isBlank() && cert_expiration == null) return emptyList()
  return listOf(
    Certification(
      type = type.orEmpty(),
      number = cert_number,
      expiration = cert_expiration,
      expire_limit = legacyExpireLimit(),
    )
  )
}

/**
 * The template key the legacy certificate maps onto, or null when the record never carried one.
 *
 * Both the enum and the string it replaced, because a record written before the enum existed still
 * carries only `cert_type` — and that one was written by hand in whatever case, so it is matched
 * case-insensitively.
 */
private fun Technician.legacyCertificationKey(): String? {
  val type = when {
    certificate_type != CertificateType.CERTIFICATE_TYPE_NONE -> certificate_type
    cert_type.isBlank() -> return null
    else -> runCatching {
      CertificateType.valueOf(
        cert_type.trim()
          .uppercase()
      )
    }
      .getOrNull() ?: return null
  }
  return when (type) {
    CertificateType.CERTIFICATE_TYPE_REPAIRMAN -> FAA_REPAIRMAN
    CertificateType.CERTIFICATE_TYPE_AMT -> FAA_AMT
    CertificateType.CERTIFICATE_TYPE_NONE -> null
  }
}

/**
 * A record predating `cert_expire_limit` says nothing about expiry, so the date it stored is what
 * decides: no date means the user never gave one, which reads as "never expires" the way the form
 * has always presented it.
 */
private fun Technician.legacyExpireLimit(): CertExpireLimit = when {
  cert_expire_limit != CertExpireLimit.CERT_EXPIRE_LIMIT_UNKNOWN -> cert_expire_limit
  cert_expiration == null -> CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES
  else -> CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES
}
