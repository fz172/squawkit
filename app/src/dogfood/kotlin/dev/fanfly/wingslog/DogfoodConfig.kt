package dev.fanfly.wingslog

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import dev.fanfly.wingslog.feature.stresstest.config.StressTestFeatureLabExtra
import dev.fanfly.wingslog.feature.stresstest.config.registerStressTestRoutes
import dev.fanfly.wingslog.feature.stresstest.config.stressTestKoinModules
import org.koin.core.module.Module

/** This source set is only compiled into the dogfood flavor. */
const val IS_DOGFOOD_BUILD = true

fun createDogfoodExtensions(): DogfoodFeatureExtensions = StressTestDogfoodExtensions()

private class StressTestDogfoodExtensions : DogfoodFeatureExtensions {
  override fun registerRoutes(builder: NavGraphBuilder, navController: NavController) =
    registerStressTestRoutes(builder, navController)

  override fun koinModules(): List<Module> = stressTestKoinModules()

  @Composable
  override fun FeatureLabExtra(navController: NavController) =
    StressTestFeatureLabExtra(navController)
}
