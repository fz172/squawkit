package dev.fanfly.wingslog.feature.featurelab.datamanager

import kotlinx.coroutines.flow.Flow

interface FeatureLabManager {
  fun observe(): Flow<FeatureFlags>
  suspend fun update(flags: FeatureFlags): Result<Unit>
}
