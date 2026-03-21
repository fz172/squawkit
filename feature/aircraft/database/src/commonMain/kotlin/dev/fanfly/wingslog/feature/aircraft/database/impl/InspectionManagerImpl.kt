package dev.fanfly.wingslog.feature.aircraft.database.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getFleetCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.aircraft.database.DueStatus
import dev.fanfly.wingslog.feature.aircraft.database.InspectionManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.fanfly.wingslog.core.database.observeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class InspectionManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : InspectionManager {

  override fun observeInspections(aircraftId: String): Flow<List<InspectionCard>> {
    val cardsRef = getCardsCollectionRef(aircraftId)
      ?: return kotlinx.coroutines.flow.flowOf(emptyList())

    return cardsRef.observeSnapshot().map { snapshot ->
      val cards = mutableListOf<InspectionCard>()
      for (doc in snapshot.documents) {
        val blobBytes = doc.getBlobAsBytes(INSPECTION_CARD_BLOB)
        if (blobBytes != null) {
          try {
            cards.add(InspectionCard.ADAPTER.decode(blobBytes))
          } catch (e: Exception) {
            logger.w(e) { "Failed to parse card ${doc.id}" }
          }
        }
      }
      cards
    }
  }

  override suspend fun addInspection(aircraftId: String, card: InspectionCard): Result<Boolean> =
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

  override suspend fun updateInspection(aircraftId: String, card: InspectionCard): Result<Boolean> =
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

  override suspend fun deleteInspection(aircraftId: String, cardId: String): Result<Boolean> = try {
    val cardsRef =
      getCardsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
    cardsRef.document(cardId).delete()
    logger.d { "Card $cardId deleted successfully." }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error deleting card $cardId" }
    Result.failure(e)
  }

  override suspend fun computeNextDue(card: InspectionCard, logs: List<MaintenanceLog>): DueStatus {
    // 1. Force overrides
    val hasForcedDate = card.force_due_date != null && (card.force_due_date!!.getEpochSecond() > 0L)
    val hasForcedTach = card.force_due_tach > 0f

    if (hasForcedDate || hasForcedTach) {
      return DueStatus(
        nextDueDate = if (hasForcedDate) {
          Instant.fromEpochSeconds(card.force_due_date!!.getEpochSecond(), card.force_due_date!!.getNano())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        } else null,
        nextDueTach = if (hasForcedTach) card.force_due_tach else null,
        isOverdue = false
      )
    }

    // 2. Compute based on rules and last maintenance
    val relevantLogs = logs.filter { card.id in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
    val latestLog = relevantLogs.firstOrNull()

    var nextDueDate: LocalDate? = null
    var nextDueTach: Float? = null
    var isOnCondition = false

    for (rule in card.rules) {
      when {
        rule.time_rule != null -> {
          val baseDate = if (latestLog?.timestamp != null) {
            Instant.fromEpochSeconds(latestLog.timestamp!!.getEpochSecond(), latestLog.timestamp!!.getNano())
              .toLocalDateTime(TimeZone.currentSystemDefault()).date
          } else {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
          }
          val calculated = baseDate.plus(rule.time_rule!!.interval_months, DateTimeUnit.MONTH)
          if (nextDueDate == null || calculated < nextDueDate) {
            nextDueDate = calculated
          }
        }

        rule.tach_rule != null -> {
          val baseTach = latestLog?.tach_time?.toFloat() ?: 0f
          val calculated = baseTach + rule.tach_rule!!.interval_hours.toFloat()
          if (nextDueTach == null || calculated < nextDueTach) {
            nextDueTach = calculated
          }
        }

        rule.on_condition_rule != null -> {
          isOnCondition = true
        }
      }
    }

    return DueStatus(
      nextDueDate = nextDueDate,
      nextDueTach = nextDueTach,
      isOnCondition = isOnCondition,
      isOverdue = false
    )
  }

  private suspend fun saveCard(
    docRef: dev.gitlive.firebase.firestore.DocumentReference,
    card: InspectionCard
  ) {
    val data = mutableMapOf<String, Any>(
      INSPECTION_CARD_BLOB to InspectionCard.ADAPTER.encode(card),
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
