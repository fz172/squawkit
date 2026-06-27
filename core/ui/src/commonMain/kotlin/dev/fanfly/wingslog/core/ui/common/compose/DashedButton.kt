package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun DashedButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  height: Dp = Spacing.buttonHeight,
) {
  val stroke = Stroke(
    width = 4f,
    pathEffect = PathEffect.dashPathEffect(
      floatArrayOf(
        10f,
        10f
      ),
      0f
    )
  )
  // 1. Get the standard colors for a card
  val colors = OutlinedTextFieldDefaults.colors()

  // 2. Extract the container color to use in drawing
  val containerColor = colors.unfocusedIndicatorColor
  val contentColor = colors.unfocusedLabelColor

  Box(
    modifier = modifier
      .height(height)
      .drawWithContent {
        drawContent()
        drawRoundRect(
          color = containerColor,
          style = stroke,
          cornerRadius = CornerRadius(Spacing.chipCornerRadius.toPx())
        )
      }
      .clip(RoundedCornerShape(Spacing.chipCornerRadius))
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Default.Add,
        contentDescription = null,
        tint = contentColor
      )
      Spacer(Modifier.width(Spacing.small))
      Text(
        label,
        color = contentColor,
        fontWeight = FontWeight.Bold
      )
    }
  }
}