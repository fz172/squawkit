package dev.fanfly.wingslog

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import org.koin.core.module.Module

interface DogfoodFeatureExtensions {
  fun registerRoutes(builder: NavGraphBuilder, navController: NavController)
  fun koinModules(): List<Module>
  @Composable fun FeatureLabExtra(navController: NavController)
}

object NoOpDogfoodExtensions : DogfoodFeatureExtensions {
  override fun registerRoutes(builder: NavGraphBuilder, navController: NavController) = Unit
  override fun koinModules(): List<Module> = emptyList()
  @Composable override fun FeatureLabExtra(navController: NavController) = Unit
}
