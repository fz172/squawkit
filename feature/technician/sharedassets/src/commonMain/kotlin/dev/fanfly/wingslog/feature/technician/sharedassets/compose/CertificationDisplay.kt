package dev.fanfly.wingslog.feature.technician.sharedassets.compose

import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.template.forKey
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.Technician
import kotlinx.datetime.TimeZone

/**
 * One of a technician's certifications as a list row reads it.
 *
 * @property label the declaring template's word for the credential, or the raw stored key when no
 *   installed template declares it — untagged rather than an error (PRD §8.6). Null for a legacy
 *   row that carried a number and nothing that names it.
 * @property expiration null when the credential never expires, or when nobody has given a date.
 */
data class CertificationLine(
  val label: String?,
  val number: String?,
  val expiration: String?,
)

/**
 * The certifications to draw for [this], resolved against what the account's templates declare.
 *
 * Goes through `resolvedCertifications`, so a record still carrying the pre-#684 single certificate
 * reads as one line rather than as none.
 */
fun Technician.certificationLines(
  offered: List<OfferedCertification>,
): List<CertificationLine> = resolvedCertifications().map { certification ->
  CertificationLine(
    label = offered.forKey(certification.type)?.label
      ?: certification.type.takeIf { it.isNotBlank() },
    number = certification.number.takeIf { it.isNotBlank() },
    // A picked wall date is stored as UTC midnight — read it back in UTC, not the device zone.
    expiration = certification.expiration
      ?.takeIf { certification.expire_limit != CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES }
      ?.toLocalDate(TimeZone.UTC)
      ?.toDisplayFormat(),
  )
}

/** The row's one-line summary: the credential, then its number. */
fun CertificationLine.summary(): String = listOfNotNull(label, number).joinToString(" · ")
