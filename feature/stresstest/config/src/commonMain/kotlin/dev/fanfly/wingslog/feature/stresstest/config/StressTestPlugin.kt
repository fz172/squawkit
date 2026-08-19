package dev.fanfly.wingslog.feature.stresstest.config

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
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsExtra
import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsNavContributor
import dev.fanfly.wingslog.feature.stresstest.StressTestScreen
import dev.fanfly.wingslog.feature.stresstest.di.stressTestModule
import org.jetbrains.compose.resources.stringResource
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import wingslog.feature.stresstest.config.generated.resources.Res
import wingslog.feature.stresstest.config.generated.resources.debug_tools_header
import wingslog.feature.stresstest.config.generated.resources.fake_data_generator_description
import wingslog.feature.stresstest.generated.resources.stress_test_title
import wingslog.feature.stresstest.generated.resources.Res as StressTestRes

const val STRESS_TEST_ROUTE = "debug_stress_test"

fun stressTestKoinModules(): List<Module> =
  listOf(stressTestModule, stressTestPluginModule)

/**
 * Contributes the Developer Options section. Bound to [DeveloperOptionsExtra] so
 * `DeveloperOptionsScreen`'s `getAll()` finds it — the screen never imports anything from here, and
 * neither does `feature:shell`, which used to have to.
 */
private val stressTestPluginModule = module {
  single { StressTestDeveloperOptionsExtra(get<AppCapability>()) } bind DeveloperOptionsExtra::class
  single { StressTestNavContributor(get<AppCapability>()) } bind DeveloperOptionsNavContributor::class
}

/**
 * Registers the Fake Data Generator screen into the settings graph.
 *
 * Was `registerStressTestRoutes(builder, navController)`, called from `ShellNavGraph` — which is why
 * `feature:shell` depended on this module at all. Contributing it through Koin instead means the
 * shell registers a screen it has never heard of, and the `isStressTestSupported` argument it used
 * to thread down here disappears with it.
 */
class StressTestNavContributor(
  private val capability: AppCapability,
) : DeveloperOptionsNavContributor {

  override fun isAvailable(): Boolean = capability.isStressTestSupported

  override fun register(builder: NavGraphBuilder, navController: NavController) {
    builder.composable(STRESS_TEST_ROUTE) {
      StressTestScreen(navController = navController)
    }
  }
}

/**
 * The Fake Data Generator entry in Developer Options.
 *
 * Was a bare `@Composable` the shell passed into `DeveloperOptionsScreen`'s single `dogfoodContent`
 * slot, which meant `feature:shell` had to depend on this module and could host only one such
 * section. Now it is resolved from Koin like any other [DeveloperOptionsExtra].
 *
 * [isAvailable] is where `isStressTestSupported` lives now — the feature that owns a section knows
 * whether it applies, so the host no longer gates it on the caller's behalf. The shell still reads
 * the same capability for [registerStressTestRoutes]; that is a different question (may the route
 * exist) from this one (should the row render).
 */
class StressTestDeveloperOptionsExtra(
  private val capability: AppCapability,
) : DeveloperOptionsExtra {

  /** Trailing: "Debug tools" reads as the end of the screen. */
  override val order: Int = 900

  override fun isAvailable(): Boolean = capability.isStressTestSupported

  @Composable
  override fun Content(onNavigate: (route: String) -> Unit) {
    Spacer(Modifier.height(Spacing.extraLarge))
    Text(
      text = stringResource(Res.string.debug_tools_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(bottom = Spacing.small),
    )
    HorizontalDivider()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onNavigate(STRESS_TEST_ROUTE) }
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
          text = stringResource(StressTestRes.string.stress_test_title),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = stringResource(Res.string.fake_data_generator_description),
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
    // No trailing divider — the host draws one after every extra.
  }
}
