package dev.fanfly.wingslog.feature.developeroptions.datamanager

import kotlinx.coroutines.flow.Flow

interface DeveloperOptionsManager {
  fun observe(): Flow<DeveloperFlags>
  suspend fun update(flags: DeveloperFlags): Result<Unit>
}
