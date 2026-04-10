package dev.fanfly.wingslog.feature.inspection.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getFleetCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.inspection.datamanager.InspectionManager
import dev.fanfly.wingslog.feature.inspection.model.DueMetadata
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class InspectionManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : InspectionManager {

  override fun observeInspections(aircraftId: String): Flow<List<InspectionCard>> {
    val cardsRef = getCardsCollectionRef(aircraftId)
      ?: return kotlinx.coroutines.flow.flowOf(emptyList())

    return cardsRef.snapshots.map { snapshot ->
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

  override suspend fun computeNextDue(
    card: InspectionCard,
    logs: List<MaintenanceLog>,
    allCards: List<InspectionCard>,
  ): DueMetadata {
    return computeNextDueRecursive(card, logs, logs, allCards, mutableSetOf())
  }

  private fun computeNextDueRecursive(
    card: InspectionCard,
    logs: List<MaintenanceLog>,
    allLogs: List<MaintenanceLog>,
    allCards: List<InspectionCard>,
    visited: MutableSet<String>,
  ): DueMetadata {
    if (card.id in visited) {
      // Cycle detected or already computed in this chain
      return DueMetadata(status = DueStatus.NORMAL)
    }
    visited.add(card.id)

    // 0. Check One-Time Completion
    val relevantLogs = logs.filter { card.id in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
    val latestLog = relevantLogs.firstOrNull()

    if (card.is_one_time && latestLog != null) {
      return DueMetadata(status = DueStatus.COMPLIED)
    }

    // 1. Force overrides
    val hasForcedDate = card.force_due_date != null && (card.force_due_date!!.getEpochSecond() > 0L)
    val hasForcedEngine = card.force_due_engine_hour > 0f

    // Determine which metric to track against based on component type
    // Airframe tracks airframe_time, others track engine_hour
    val currentMetricTime =
      if (card.component == InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME) {
        allLogs.filter { it.airframe_time > 0.0 }.maxOfOrNull { it.airframe_time }?.toFloat() ?: 0f
      } else {
        allLogs.filter { it.engine_hour > 0.0 }.maxOfOrNull { it.engine_hour }?.toFloat() ?: 0f
      }

    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    if (hasForcedDate || hasForcedEngine) {
      val nextDueDate = if (hasForcedDate) {
        Instant.fromEpochSeconds(
          card.force_due_date!!.getEpochSecond(),
          card.force_due_date!!.getNano()
        )
          .toLocalDateTime(TimeZone.currentSystemDefault()).date
      } else null
      val nextDueEngine = if (hasForcedEngine) card.force_due_engine_hour else null

      val status = when {
        (nextDueDate != null && nextDueDate < currentDate) ||
            (nextDueEngine != null && nextDueEngine < currentMetricTime) -> DueStatus.OVERDUE

        (nextDueDate != null && nextDueDate <= currentDate.plus(1, DateTimeUnit.MONTH)) ||
            (nextDueEngine != null && nextDueEngine <= currentMetricTime + 10f) -> DueStatus.DUE_SOON

        else -> DueStatus.NORMAL
      }

      return DueMetadata(
        nextDueDate = nextDueDate,
        nextDueEngine = nextDueEngine,
        status = status
      )
    }

    // 2. Compute based on rules
    var nextDueDate: LocalDate? = null
    var nextDueEngine: Float? = null
    var isOnCondition = false
    var isImmediate = false

    for (rule in card.rules) {
      val timeRule = rule.time_rule
      val engineRule = rule.engine_hour_rule
      val onConditionRule = rule.on_condition_rule
      val linkedRule = rule.linked_rule
      val immediateRule = rule.immediate_rule

      when {
        timeRule != null -> {
          val baseDate = if (latestLog?.timestamp != null) {
            Instant.fromEpochSeconds(
              latestLog.timestamp!!.getEpochSecond(),
              latestLog.timestamp!!.getNano()
            )
              .toLocalDateTime(TimeZone.currentSystemDefault()).date
          } else {
            // If never done, we assume it's due from the beginning of the aircraft logs or now.
            // Using aircraft creation date would be better, but we don't have it here easily.
            // Using the earliest log date as a proxy.
            allLogs.lastOrNull()?.timestamp?.let {
              Instant.fromEpochSeconds(it.getEpochSecond(), it.getNano())
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            } ?: currentDate
          }
          val calculated = baseDate.plus(timeRule.interval_months, DateTimeUnit.MONTH)
          if (nextDueDate == null || calculated < nextDueDate) {
            nextDueDate = calculated
          }
        }

        engineRule != null -> {
          val baseEngine =
            if (card.component == InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME) {
              latestLog?.airframe_time?.toFloat() ?: 0f
            } else {
              latestLog?.engine_hour?.toFloat() ?: 0f
            }
          val calculated = baseEngine + engineRule.interval_hours
          if (nextDueEngine == null || calculated < nextDueEngine) {
            nextDueEngine = calculated
          }
        }

        onConditionRule != null -> {
          isOnCondition = true
        }

        immediateRule != null -> {
          isImmediate = true
        }

        linkedRule != null -> {
          val parentCard = allCards.find { it.id == linkedRule.parent_inspection_id }
          if (parentCard != null) {
            // Find when THIS card was last completed
            val latestLogEpoch = latestLog?.timestamp?.getEpochSecond() ?: 0L

            // Compute parent's due status as of the last time THIS card was completed.
            // This ensures that if the parent is done but THIS card is skipped, 
            // THIS card remains due/overdue based on the OLD parent cycle.
            val parentLogs = if (latestLog == null) {
              emptyList()
            } else {
              allLogs.filter { (it.timestamp?.getEpochSecond() ?: 0L) <= latestLogEpoch }
            }

            val parentMetadata =
              computeNextDueRecursive(parentCard, parentLogs, allLogs, allCards, visited)

            // Inherit due properties from parent
            val pNextDate = parentMetadata.nextDueDate
            if (pNextDate != null && (nextDueDate == null || pNextDate < nextDueDate)) {
              nextDueDate = pNextDate
            }
            val pNextEngine = parentMetadata.nextDueEngine
            if (pNextEngine != null && (nextDueEngine == null || pNextEngine < nextDueEngine)) {
              nextDueEngine = pNextEngine
            }
            if (parentMetadata.isOnCondition) isOnCondition = true
            if (parentMetadata.isImmediate) isImmediate = true
          }
        }
      }
    }

    val status = when {
      isImmediate -> DueStatus.OVERDUE
      (nextDueDate != null && nextDueDate < currentDate) ||
          (nextDueEngine != null && nextDueEngine < currentMetricTime) -> DueStatus.OVERDUE

      (nextDueDate != null && nextDueDate <= currentDate.plus(1, DateTimeUnit.MONTH)) ||
          (nextDueEngine != null && nextDueEngine <= currentMetricTime + 10f) -> DueStatus.DUE_SOON

      else -> DueStatus.NORMAL
    }

    return DueMetadata(
      nextDueDate = nextDueDate,
      nextDueEngine = nextDueEngine,
      isOnCondition = isOnCondition,
      isImmediate = isImmediate,
      status = status
    )
  }

  private suspend fun saveCard(
    docRef: DocumentReference,
    card: InspectionCard,
  ) {
    val data = mutableMapOf(
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
