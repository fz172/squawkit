package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getFleetCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TaskDataManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : TaskDataManager {

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeTasks(aircraftId: String): Flow<List<MaintenanceTask>> {
    return firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping tasks observation for aircraft $aircraftId" }
        flowOf(emptyList())
      } else {
        val cardsRef = getCardsCollectionRef(aircraftId)
          ?: return@flatMapLatest flowOf(emptyList())

        cardsRef.snapshots.map { snapshot ->
          val cards = mutableListOf<MaintenanceTask>()
          for (doc in snapshot.documents) {
            val blobBytes = doc.getBlobAsBytes(INSPECTION_CARD_BLOB)
            if (blobBytes != null) {
              try {
                cards.add(MaintenanceTask.ADAPTER.decode(blobBytes))
              } catch (e: Exception) {
                logger.w(e) { "Failed to parse card ${doc.id}" }
              }
            }
          }
          cards.toList()
        }.catch { e ->
          logger.w(e) { "Error observing tasks for aircraft $aircraftId" }
          emit(emptyList())
        }
      }
    }
  }

  override suspend fun addTask(aircraftId: String, card: MaintenanceTask): Result<Boolean> =
    try {
      val cardsRef =
        getCardsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
      val newDocRef =
        if (card.id.isEmpty()) cardsRef.document(generateRandomId()) else cardsRef.document(card.id)
      val finalCard = if (card.id.isEmpty()) card.copy(id = newDocRef.id) else card

      saveCard(newDocRef, finalCard)
      Result.success(true)
    } catch (e: Exception) {
      logger.w(e) { "Error adding card" }
      Result.failure(e)
    }

  override suspend fun updateTask(aircraftId: String, card: MaintenanceTask): Result<Boolean> =
    try {
      val cardsRef =
        getCardsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
      val docRef = cardsRef.document(card.id)
      saveCard(docRef, card)
      Result.success(true)
    } catch (e: Exception) {
      logger.w(e) { "Error updating card ${card.id}" }
      Result.failure(e)
    }

  override suspend fun deleteTask(aircraftId: String, cardId: String): Result<Boolean> = try {
    val cardsRef =
      getCardsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
    cardsRef.document(cardId).delete()
    logger.d { "Card $cardId deleted successfully." }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error deleting card $cardId" }
    Result.failure(e)
  }

  private suspend fun saveCard(
    docRef: DocumentReference,
    card: MaintenanceTask,
  ) {
    val data = mutableMapOf(
      INSPECTION_CARD_BLOB to MaintenanceTask.ADAPTER.encode(card),
      TITLE_FIELD to card.title,
      COMPONENT_FIELD to card.component.name
    )
    docRef.setEncoded(data, merge = true)
  }

  private fun getCardsCollectionRef(aircraftId: String): CollectionReference? {
    return firestore.getFleetCollectionRef(firebaseAuth)?.document(aircraftId)
      ?.collection(INSPECTION_CARDS_COLLECTION)
  }

  companion object {
    private val logger = Logger.withTag("InspectionManagerImpl")
    private const val INSPECTION_CARDS_COLLECTION = "inspection_cards"
    private const val INSPECTION_CARD_BLOB = "inspection_card_blob"
    private const val TITLE_FIELD = "title"
    private const val COMPONENT_FIELD = "component"
  }
}
