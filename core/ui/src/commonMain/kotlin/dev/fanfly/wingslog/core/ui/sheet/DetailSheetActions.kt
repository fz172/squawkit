package dev.fanfly.wingslog.core.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * The row a sheet's actions sit in — state changes, the route to the edit form, delete. One row,
 * every action the same width and height, so a sheet with one action and a sheet with three read
 * the same way.
 */
@Composable
fun DetailSheetActionRow(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth()
      .height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    content = content,
  )
}

/**
 * One action in a [DetailSheetActionRow]. [primary] is the filled one — at most one per row — and
 * [destructive] is outlined in the error colour. A label wraps rather than truncates when the row
 * is crowded; [menu] is anchored to the button, for an action that opens options.
 */
@Composable
fun RowScope.DetailSheetAction(
  label: String,
  onClick: () -> Unit,
  primary: Boolean = false,
  destructive: Boolean = false,
  menu: @Composable () -> Unit = {},
) {
  Box(
    modifier = Modifier.weight(1f)
      .fillMaxHeight(),
  ) {
    val shape = RoundedCornerShape(Spacing.buttonCornerRadius)
    val buttonModifier = Modifier.fillMaxSize()
    val padding = PaddingValues(Spacing.small)
    val text: @Composable RowScope.() -> Unit =
      { Text(label, textAlign = TextAlign.Center) }
    when {
      destructive -> OutlinedButton(
        onClick = onClick,
        modifier = buttonModifier,
        shape = shape,
        contentPadding = padding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        content = text,
      )

      primary -> Button(
        onClick = onClick,
        modifier = buttonModifier,
        shape = shape,
        contentPadding = padding,
        content = text,
      )

      else -> OutlinedButton(
        onClick = onClick,
        modifier = buttonModifier,
        shape = shape,
        contentPadding = padding,
        content = text,
      )
    }
    menu()
  }
}

/**
 * The route to the edit form, on the title row — icon only, because updating a record is the rare
 * action next to resolving or logging against it, and a labelled button there squeezes the title.
 */
@Composable
fun DetailSheetEditAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(Icons.Outlined.Edit, contentDescription = label)
  }
}
