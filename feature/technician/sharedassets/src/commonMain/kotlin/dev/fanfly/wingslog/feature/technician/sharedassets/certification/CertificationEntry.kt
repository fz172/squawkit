package dev.fanfly.wingslog.feature.technician.sharedassets.certification

import dev.fanfly.wingslog.core.template.CUSTOM_CERTIFICATION_PREFIX
import dev.fanfly.wingslog.thing.CertExpireLimit
import kotlin.time.Instant

/**
 * One certification as the form holds it mid-edit — a [dev.fanfly.wingslog.thing.Certification]
 * with the expiry as an [Instant], which is what the date picker deals in.
 */
data class CertificationEntry(
  val type: String,
  val number: String = "",
  val expireLimit: CertExpireLimit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
  val expiration: Instant? = null,
  /** The user's own word, on a `custom_N` entry only. Empty for anything a template names. */
  val label: String = "",
) {
  val isCustom: Boolean get() = type.startsWith(CUSTOM_CERTIFICATION_PREFIX)
}
