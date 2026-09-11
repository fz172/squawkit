package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing

// The older button vocabulary, still used by the surfaces the sign-in card's redesign did not
// touch: the guest-upgrade sheet (feature/login/upgrade) and the email step's own actions. The card
// itself is a list of rows now and uses none of this — see LoginCommon's LoginRow.

/** Apple's button colours for the upgrade sheet, which still uses a black Apple button. */
internal val AppleButtonBackground = Color(0xFF000000)
internal val AppleButtonContent = Color(0xFFFFFFFF)

internal val LoginButtonLabelStyle = TextStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = 15.sp,
)
internal val LoginSecondaryLabelStyle = TextStyle(fontSize = 15.sp)

/** Every button on these surfaces is this tall. */
internal val LoginButtonHeight = 56.dp

/**
 * The inside of a sign-in button: leading icon, then the label.
 *
 * Shared because the alignment is the whole point. Each button used to wrap its own centred `Row`,
 * which put the icon at a different x in every button — the icon's position depended on the label's
 * width, so a column of them read as ragged. Here the icon is pinned to the leading edge and the
 * label is centred in what is left, balanced by a spacer the icon's width so the label still sits in
 * the middle of the button rather than off to the right.
 *
 * Public, not internal, because the web host's own surfaces call it too. Keep the signature stable.
 *
 * [iconSize] must match what [icon] actually renders: it sizes the trailing spacer, and if the two
 * disagree the label stops being centred, which is the bug this exists to prevent.
 */
@Composable
fun LoginButtonContent(
  label: String,
  labelStyle: TextStyle = LoginButtonLabelStyle,
  iconSize: Dp = Spacing.xLarge,
  icon: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    icon()
    Text(
      text = label,
      style = labelStyle,
      textAlign = TextAlign.Center,
      maxLines = 1,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = Spacing.small),
    )
    Spacer(Modifier.size(iconSize))
  }
}
