package dev.fanfly.wingslog.feature.technician.sharedassets.compose

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.template.forKey
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.Technician
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.certification_expires_on
import wingslog.feature.technician.sharedassets.generated.resources.unnamed_certification

/**
 * One of a technician's certifications as a list row reads it.
 *
 * @property label what names the credential: the declaring template's word, the user's own for a
 *   custom one, or the raw stored key when no installed template declares it — untagged rather than
 *   an error (PRD §8.6). Null for a legacy row that carried a number and nothing naming it.
 * @property expiration null when the credential never expires, or when nobody has given a date.
 */
data class CertificationLine(
  val label: String?,
  val number: String?,
  val expiration: String?,
)

/**
 * The certifications to draw for [this], resolved against what this build knows.
 *
 * Goes through `resolvedCertifications`, so a record still carrying the pre-#684 single certificate
 * reads as one line rather than as none.
 */
fun Technician.certificationLines(
  offered: List<OfferedCertification>,
): List<CertificationLine> = resolvedCertifications().map { certification ->
  CertificationLine(
    // The user's own word wins: a custom certification is named by the person who added it, and no
    // template declares its key.
    label = certification.label.takeIf { it.isNotBlank() }
      ?: offered.forKey(certification.type)?.label
      ?: certification.type.takeIf { it.isNotBlank() },
    number = certification.number.takeIf { it.isNotBlank() },
    // A picked wall date is stored as UTC midnight — read it back in UTC, not the device zone.
    expiration = certification.expiration
      ?.takeIf { certification.expire_limit != CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES }
      ?.toLocalDate(TimeZone.UTC)
      ?.toDisplayFormat(),
  )
}

/**
 * The whole credential on one line — `A&P Mechanic · A7584747 (Exp 08/31/2031)`.
 *
 * One line rather than the number and the date stacked: they are one fact about one credential, and
 * a person holding three of them reads as three rows instead of six.
 */
@Composable
fun CertificationLine.summary(): String {
  val head = listOfNotNull(
    label ?: stringResource(Res.string.unnamed_certification),
    number,
  ).joinToString(SEPARATOR)
  val expiry = expiration ?: return head
  return "$head " + stringResource(
    Res.string.certification_expires_on,
    expiry
  )
}

/**
 * Just the credential and its number, no expiry — what a one-line picker subtitle has room for when
 * a person may hold several.
 */
fun CertificationLine.short(): String =
  listOfNotNull(label, number).joinToString(SEPARATOR)

private const val SEPARATOR = " · "
