package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TaskDataManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : TaskDataManager {

  private val store: EntityStore<MaintenanceTask> =
    storeFactory.create(CollectionKind.MaintenanceTask)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeTasks(aircraftId: String): Flow<List<MaintenanceTask>> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping tasks observation for aircraft $aircraftId" }
        flowOf(emptyList())
      } else {
        store.observeAll(EntityScope.aircraftChild(user.uid, aircraftId))
          .map { rows -> rows.map { it.value } }
          .catch { e ->
            logger.w(e) { "Error observing tasks for aircraft $aircraftId" }
            emit(emptyList())
          }
      }
    }

  override suspend fun addTask(aircraftId: String, card: MaintenanceTask): Result<Boolean> =
    runCatching {
      val uid = requireUid()
      val withId = if (card.id.isEmpty()) card.copy(id = generateRandomId()) else card
      store.put(withId.id, withId, EntityScope.aircraftChild(uid, aircraftId))
      true
    }.onFailure { logger.w(it) { "Error adding task" } }

  override suspend fun updateTask(aircraftId: String, card: MaintenanceTask): Result<Boolean> =
    runCatching {
      val uid = requireUid()
      store.put(card.id, card, EntityScope.aircraftChild(uid, aircraftId))
      true
    }.onFailure { logger.w(it) { "Error updating task ${card.id}" } }

  override suspend fun deleteTask(aircraftId: String, cardId: String): Result<Boolean> =
    runCatching {
      val uid = requireUid()
      store.delete(cardId, EntityScope.aircraftChild(uid, aircraftId))
      true
    }.onFailure { logger.w(it) { "Error deleting task $cardId" } }

  private fun requireUid(): String =
    firebaseAuth.currentUser?.uid ?: error("Cannot mutate tasks when no user is signed in")

  companion object {
    private val logger = Logger.withTag("TaskDataManagerImpl")
  }
}
