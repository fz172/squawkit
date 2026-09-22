package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors

/**
 * Read-only informational banner for summarising a multi-step form's current state.
 *
 * Styled to look unlike an input: a faint tint of the tone with a hairline border of the same
 * colour, and nothing to press. The tone rides the tint and the label — never a side stripe, which
 * `DESIGN.md` rules out as a callout accent.
 *
 * Use [PreviewBannerTone] to communicate semantic state:
 * - [PreviewBannerTone.Neutral] — nothing configured yet, or no change from default
 * - [PreviewBannerTone.Active] — form has meaningful input; showing a live preview
 * - [PreviewBannerTone.Warn]   — a cycle-skip or destructive adjustment is pending (amber)
 */
enum class PreviewBannerTone { Neutral, Active, Warn }

@Composable
fun PreviewBanner(
  label: String,
  hint: String,
  primary: AnnotatedString,
  secondary: AnnotatedString,
  tone: PreviewBannerTone = PreviewBannerTone.Neutral,
  modifier: Modifier = Modifier,
  /** A third, quieter line — an override or a caveat — shown only when there is one. */
  tertiary: AnnotatedString? = null,
) {
  val accentColor = when (tone) {
    PreviewBannerTone.Neutral -> MaterialTheme.colorScheme.outline
    PreviewBannerTone.Active -> MaterialTheme.colorScheme.primary
    PreviewBannerTone.Warn -> MaterialTheme.statusColors.caution.accent
  }
  val primaryTextColor = when (tone) {
    PreviewBannerTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
  }
  val bannerShape = RoundedCornerShape(Spacing.cardCornerRadius)
  // Neutral has no tone to tint with, so it keeps the near-transparent wash it always had.
  val tint = if (tone == PreviewBannerTone.Neutral) {
    MaterialTheme.colorScheme.onSurface.copy(alpha = NEUTRAL_WASH)
  } else {
    accentColor.copy(alpha = TONE_WASH)
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(bannerShape)
      .background(tint)
      .border(
        Spacing.hairline,
        accentColor.copy(alpha = TONE_BORDER),
        bannerShape
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
          ),
          color = accentColor,
        )
        Box(
          modifier = Modifier
            .size(Spacing.extraSmall)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Text(
          hint,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        primary,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
        ),
        color = primaryTextColor,
      )
      Text(
        secondary,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (tertiary != null) {
        Text(
          tertiary,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private const val NEUTRAL_WASH = 0.03f
private const val TONE_WASH = 0.08f
private const val TONE_BORDER = 0.35f
