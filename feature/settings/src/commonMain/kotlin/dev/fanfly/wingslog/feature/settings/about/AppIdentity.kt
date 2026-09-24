package dev.fanfly.wingslog.feature.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.fanfly.wingslog.core.ui.hero.heroBob
import dev.fanfly.wingslog.core.ui.hero.rememberHeroPulse
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.app_icon
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** The app mark (bobbing, like the heroes) and its name; the version is a row below. */
@Composable
internal fun AppIdentity() {
  val pulse = rememberHeroPulse()
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = Spacing.large),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    // The store icon itself, in colour; only the corners are ours — the same radius the launchers use.
    Image(
      painter = painterResource(CoreRes.drawable.app_icon),
      contentDescription = null,
      modifier = Modifier
        .size(AppIconSize)
        .heroBob { pulse.value }
        .clip(RoundedCornerShape(Spacing.extraLarge)),
    )
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = stringResource(CoreRes.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
