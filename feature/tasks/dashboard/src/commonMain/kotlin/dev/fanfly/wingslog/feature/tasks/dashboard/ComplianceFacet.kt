package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.thing.ComplianceType

internal val COMPLIANCE_OPTIONS = listOf(
  ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE,
  ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN,
)

/** The lexicon’s words: “Inspection” / “Airworthiness Directive” / “Service Bulletin” on an aircraft. */
@Composable
internal fun complianceLabel(type: ComplianceType): String {
  val lexicon = LocalThingLexicon.current
  return when (type) {
    ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> lexicon.compliance_mandatory?.singular.orEmpty()
    ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> lexicon.compliance_advisory?.singular.orEmpty()
    else -> LexiconFormatter.titleCase(lexicon.taskNoun)
  }
}
