package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.common.compose.rememberSelectAllOnFocus
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun IntervalNumberInput(
  value: String,
  onChange: (String) -> Unit,
  prefix: String,
  suffix: String,
  keyboard: KeyboardType,
) {
  val borderColor = if (value.isNotBlank()) MaterialTheme.colorScheme.primary
  else MaterialTheme.colorScheme.outlineVariant

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .height(Spacing.buttonHeight)
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .padding(horizontal = Spacing.medium),
  ) {
    Text(
      prefix,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = Spacing.small),
    )
    // Selected whole on focus: these open holding a number — the preset's interval, the current
    // reading — and the user is typing a different one, not editing this one digit by digit.
    val field = rememberSelectAllOnFocus(value) { v ->
      val filtered = if (keyboard == KeyboardType.Decimal) {
        v.filter { it.isDigit() || it == '.' }
      } else {
        v.filter { it.isDigit() }
      }
      onChange(filtered)
    }
    BasicTextField(
      value = field.value,
      onValueChange = field.onValueChange,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = keyboard),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      textStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurface,
      ),
      modifier = Modifier.weight(1f)
        .then(field.modifier),
      decorationBox = { inner ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (value.isEmpty()) {
            Text(
              "0",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
          }
          inner()
        }
      },
    )
    Text(
      suffix,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = Spacing.small),
    )
  }
}
