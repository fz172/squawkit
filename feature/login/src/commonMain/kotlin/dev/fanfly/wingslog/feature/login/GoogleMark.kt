package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.google_logo
import wingslog.feature.login.generated.resources.ic_google_rd_na

/** Google's mark, on a white disc when the row beneath it is accented. */
@Composable
internal fun GoogleMark(onAccent: Boolean) {
  val mark = @Composable {
    Icon(
      painter = painterResource(Res.drawable.ic_google_rd_na),
      contentDescription = stringResource(Res.string.google_logo),
      modifier = Modifier.size(16.dp),
      tint = Color.Unspecified,
    )
  }
  if (onAccent) {
    Box(
      modifier = Modifier
        .size(26.dp)
        .background(Color.White, CircleShape),
      contentAlignment = Alignment.Center,
      content = { mark() },
    )
  } else {
    mark()
  }
}
