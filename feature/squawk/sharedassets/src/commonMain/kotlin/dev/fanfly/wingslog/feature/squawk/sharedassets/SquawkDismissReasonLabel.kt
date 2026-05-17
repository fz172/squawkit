package dev.fanfly.wingslog.feature.squawk.sharedassets

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_reason_duplicate
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_reason_intended_behavior
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_reason_not_reproducible
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_reason_obsolete

@Composable
fun SquawkDismissReason.toLabel(): String = when (this) {
  SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE -> stringResource(Res.string.dismiss_reason_obsolete)
  SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE -> stringResource(
    Res.string.dismiss_reason_not_reproducible
  )

  SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE -> stringResource(Res.string.dismiss_reason_duplicate)
  SquawkDismissReason.SQUAWK_DISMISS_REASON_INTENDED_BEHAVIOR -> stringResource(
    Res.string.dismiss_reason_intended_behavior
  )

  else -> ""
}
