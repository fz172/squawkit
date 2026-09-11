package dev.fanfly.wingslog.feature.login

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

