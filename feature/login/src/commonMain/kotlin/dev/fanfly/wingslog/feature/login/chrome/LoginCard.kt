package dev.fanfly.wingslog.feature.login.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandMonoFamily

/**
 * The bordered card holding the sign-in rows: a small "SIGN IN" heading, an optional status on the
 * right, then the rows themselves.
 *
 * [content] should be [LoginRow]s separated by [LoginRowDivider]; they are clipped to the card so a
 * pressed row's corners follow the border rather than squaring it off.
 */
@Composable
internal fun LoginCard(
  heading: String? = null,
  status: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val monoFamily = rememberBrandMonoFamily()
  val labelStyle = TextStyle(
    fontFamily = monoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    letterSpacing = 1.2.sp,
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(LoginCardShape)
      .background(MaterialTheme.colorScheme.surface)
      .border(
        Spacing.hairline,
        MaterialTheme.colorScheme.outlineVariant,
        LoginCardShape
      ),
  ) {
    // Omitted where the surface already says what the rows are for — the upgrade sheet has its own
    // title above the card, and a second "SIGN IN" under it would just be a label on a label.
    if (heading != null || status != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = RowPadding,
            end = RowPadding,
            top = 20.dp,
            bottom = 12.dp
          ),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = heading.orEmpty(),
          style = labelStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status != null) {
          Text(
            text = status,
            style = labelStyle,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
      LoginRowDivider()
    }
    content()
  }
}
