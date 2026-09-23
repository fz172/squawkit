package dev.fanfly.wingslog.feature.dashboard.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.SkeletonBlock
import dev.fanfly.wingslog.core.ui.common.compose.skeletonPulse
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** The dashboard's outline: title, the status card, the meter strip, then activity. */
@Composable
internal fun DashboardSkeleton() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .skeletonPulse()
      .padding(Spacing.screenPadding),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    SkeletonBlock(
      Modifier.fillMaxWidth(0.5f)
        .height(Spacing.huge)
    )
    SkeletonBlock(
      Modifier.fillMaxWidth()
        .height(Spacing.buttonHeight)
    )
    SkeletonBlock(
      Modifier.fillMaxWidth()
        .height(Spacing.massive * 3)
    )
    SkeletonBlock(
      Modifier.fillMaxWidth()
        .height(Spacing.rowHeight)
    )
    SkeletonBlock(
      Modifier.fillMaxWidth()
        .height(Spacing.massive * 4)
    )
  }
}
