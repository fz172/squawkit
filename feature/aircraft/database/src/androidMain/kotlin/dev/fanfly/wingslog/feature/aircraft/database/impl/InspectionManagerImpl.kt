package dev.fanfly.wingslog.feature.aircraft.database.impl

import co.touchlab.kermit.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.database.common.getFleetCollectionRef
import dev.fanfly.wingslog.feature.aircraft.database.DueStatus
import dev.fanfly.wingslog.feature.aircraft.database.InspectionManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InspectionManagerImpl(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : InspectionManager {

    override fun observeInspections(aircraftId: String): Flow<List<InspectionCard>> = callbackFlow {
        val ref = getInspectionsCollectionRef(aircraftId)
        if (ref == null) {
            trySend(emptyList())
            close(Exception("Inspections reference is null (user likely not logged in)"))
            return@callbackFlow
        }

        val listener = ref.addSnapshotListener { snapshot, e ->
            if (e != null) {
                logger.w(e) { "Listen failed for inspections of aircraft $aircraftId" }
                close(e)
                return@addSnapshotListener
            }

            val cards = mutableListOf<InspectionCard>()
            if (snapshot != null && !snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    val blob = doc.getBlob(INSPECTION_CARD_BLOB)
                    if (blob != null) {
                        try {
                            cards.add(InspectionCard.ADAPTER.decode(blob.toBytes()))
                        } catch (ex: Exception) {
                            logger.w(ex) { "Failed to parse inspection card ${doc.id}" }
                        }
                    }
                }
            }
            trySend(cards)
        }

        awaitClose { listener.remove() }
    }

    override suspend fun addInspection(aircraftId: String, card: InspectionCard): Result<Boolean> = try {
        val ref = getInspectionsCollectionRef(aircraftId)
            ?: return Result.failure(Exception("User not logged in"))

        val docRef = if (card.id.isEmpty()) ref.document() else ref.document(card.id)
        val finalCard = if (card.id.isEmpty()) {
            card.copy(id = docRef.id)
        } else {
            card
        }

        saveCard(docRef, finalCard)
        Result.success(true)
    } catch (e: Exception) {
        logger.w(e) { "Error adding inspection card" }
        Result.failure(e)
    }

    override suspend fun updateInspection(aircraftId: String, card: InspectionCard): Result<Boolean> = try {
        val ref = getInspectionsCollectionRef(aircraftId)
            ?: return Result.failure(Exception("User not logged in"))
        saveCard(ref.document(card.id), card)
        Result.success(true)
    } catch (e: Exception) {
        logger.w(e) { "Error updating inspection card ${card.id}" }
        Result.failure(e)
    }

    override suspend fun deleteInspection(aircraftId: String, cardId: String): Result<Boolean> = try {
        val ref = getInspectionsCollectionRef(aircraftId)
            ?: return Result.failure(Exception("User not logged in"))
        ref.document(cardId).delete().await()
        logger.d { "Inspection card $cardId deleted." }
        Result.success(true)
    } catch (e: Exception) {
        logger.w(e) { "Error deleting inspection card $cardId" }
        Result.failure(e)
    }

    override suspend fun computeNextDue(
        card: InspectionCard,
        logs: List<MaintenanceLog>,
    ): DueStatus {
        // Force overrides take precedence
        val forceDueDate = card.force_due_date
        val forceDueTach = card.force_due_tach

        val hasForcedDate = forceDueDate != null && (forceDueDate.epochSecond > 0 || forceDueDate.nano > 0)
        val hasForcedTach = forceDueTach > 0f

        if (hasForcedDate || hasForcedTach) {
            val nextDueDate = if (hasForcedDate) {
                Instant.ofEpochSecond(forceDueDate!!.epochSecond)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            } else null
            val nextDueTach = if (hasForcedTach) forceDueTach else null
            val today = LocalDate.now()
            val isOverdue = (nextDueDate != null && !today.isBefore(nextDueDate)) ||
                    (nextDueTach != null && false) // tach overdue needs current tach — leave false here
            return DueStatus(
                nextDueDate = nextDueDate,
                nextDueTach = nextDueTach,
                isOnCondition = false,
                isOverdue = isOverdue,
            )
        }

        // Find most recent log associated with this card
        val associatedLogs = logs
            .filter { card.id in it.inspection_ids }
            .sortedByDescending { it.timestamp?.epochSecond ?: 0L }

        val today = LocalDate.now()
        var resultDate: LocalDate? = null
        var resultTach: Float? = null
        var isOnCondition = false

        for (rule in card.rules) {
            if (rule.time_rule != null) {
                val timeRule = rule.time_rule!!
                val intervalMonths = timeRule.interval_months.toLong()
                val baseDate = if (associatedLogs.isNotEmpty()) {
                    Instant.ofEpochSecond(associatedLogs.first().timestamp?.epochSecond ?: 0L)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                } else {
                    today
                }
                val candidate = baseDate.plusMonths(intervalMonths)
                if (resultDate == null || candidate.isBefore(resultDate)) {
                    resultDate = candidate
                }
            } else if (rule.tach_rule != null) {
                val tachRule = rule.tach_rule!!
                val intervalHours: Float = tachRule.interval_hours
                val baseTach: Float = if (associatedLogs.isNotEmpty()) {
                    // tach_time might be Double or Float in Wire, we must convert to Float
                    associatedLogs.first().tach_time.toFloat()
                } else {
                    0f
                }
                val candidate: Float = baseTach + intervalHours
                if (resultTach == null || candidate < resultTach) {
                    resultTach = candidate
                }
            } else if (rule.on_condition_rule != null) {
                isOnCondition = true
            }
        }

        val isOverdue = resultDate != null && !today.isBefore(resultDate)

        return DueStatus(
            nextDueDate = resultDate,
            nextDueTach = resultTach,
            isOnCondition = isOnCondition,
            isOverdue = isOverdue,
        )
    }

    private suspend fun saveCard(
        docRef: com.google.firebase.firestore.DocumentReference,
        card: InspectionCard,
    ) {
        val data = hashMapOf<String, Any>(
            INSPECTION_CARD_BLOB to Blob.fromBytes(InspectionCard.ADAPTER.encode(card)),
            TITLE_FIELD to card.title,
            COMPONENT_FIELD to card.component.name,
        )
        docRef.set(data, SetOptions.merge()).await()
    }

    private fun getInspectionsCollectionRef(aircraftId: String): CollectionReference? {
        return firestore.getFleetCollectionRef(firebaseAuth)
            ?.document(aircraftId)
            ?.collection(INSPECTIONS_COLLECTION)
    }

    companion object {
        private val logger = Logger.withTag("InspectionManagerImpl")
        private const val INSPECTIONS_COLLECTION = "inspections"
        private const val INSPECTION_CARD_BLOB = "inspection_card_blob"
        private const val TITLE_FIELD = "title"
        private const val COMPONENT_FIELD = "component"
    }
}
