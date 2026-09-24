package dev.fanfly.wingslog.feature.login.chrome

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material's disabled opacity, used for the rows a running request has locked. */
internal const val DisabledAlpha = 0.38f

/** Every sign-in row is this tall. */
internal val LoginRowHeight = 60.dp

/** The card, and the column it sits in, never grow past this. */
internal val LoginCardWidth = 400.dp

internal val LoginCardShape = RoundedCornerShape(12.dp)

internal val LoginErrorStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp)

// Button metrics for the surfaces that still use plain buttons: the email step's actions, and the
// upgrade sheet's confirm/merge sheets. The sign-in card and the upgrade sheet's provider picker are
// lists of rows now and use none of this — see LoginCommon's LoginRow.

internal val LoginButtonLabelStyle = TextStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = 15.sp,
)
internal val LoginSecondaryLabelStyle = TextStyle(fontSize = 15.sp)

/** Every button on these surfaces is this tall. */
internal val LoginButtonHeight = 56.dp
