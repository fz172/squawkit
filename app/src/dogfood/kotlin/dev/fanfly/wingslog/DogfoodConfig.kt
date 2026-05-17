package dev.fanfly.wingslog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.stresstest.StressTestScreen
import dev.fanfly.wingslog.feature.stresstest.di.stressTestModule
import org.koin.core.module.Module

fun createDogfoodExtensions(): DogfoodFeatureExtensions =
  StressTestDogfoodExtensions()

private const val STRESS_TEST_ROUTE = "debug_stress_test"

private class StressTestDogfoodExtensions : DogfoodFeatureExtensions {

  override fun registerRoutes(
    builder: NavGraphBuilder,
    navController: NavController
  ) {
    builder.composable(STRESS_TEST_ROUTE) {
      StressTestScreen(navController = navController)
    }
  }

  override fun koinModules(): List<Module> = listOf(stressTestModule)

  @Composable
  override fun FeatureLabExtra(navController: NavController) {
    Spacer(Modifier.height(Spacing.extraLarge))
    Text(
      text = "DEBUG TOOLS",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(bottom = Spacing.small),
    )
    HorizontalDivider()
    Row(
      modifier = Modifier
          .fillMaxWidth()
          .clickable { navController.navigate(STRESS_TEST_ROUTE) }
          .padding(vertical = Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.BugReport,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = Spacing.medium),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Fake Data Generator",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = "Populate fake aircraft, logs, squawks, and tasks for UI testing",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    HorizontalDivider()
  }
}
