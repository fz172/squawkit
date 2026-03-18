package dev.fanfly.wingslog.feature.aircraft.database.impl

import com.google.common.flogger.FluentLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionRule.RuleCase
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.database.DueStatus
import dev.fanfly.wingslog.feature.aircraft.database.InspectionManager
import dev.fanfly.wingslog.core.database.common.getFleetCollectionRef
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class InspectionManagerImpl @Inject internal constructor(
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
                logger.atWarning().withCause(e).log("Listen failed for inspections of aircraft $aircraftId")
                close(e)
                return@addSnapshotListener
            }

            val cards = mutableListOf<InspectionCard>()
            if (snapshot != null && !snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    val blob = doc.getBlob(INSPECTION_CARD_BLOB)
                    if (blob != null) {
                        try {
                            cards.add(InspectionCard.parseFrom(blob.toBytes()))
                        } catch (ex: Exception) {
                            logger.atWarning().withCause(ex).log("Failed to parse inspection card ${doc.id}")
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
            card.toBuilder().setId(docRef.id).build()
        } else {
            card
        }

        saveCard(docRef, finalCard)
        Result.success(true)
    } catch (e: Exception) {
        logger.atWarning().withCause(e).log("Error adding inspection card")
        Result.failure(e)
    }

    override suspend fun updateInspection(aircraftId: String, card: InspectionCard): Result<Boolean> = try {
        val ref = getInspectionsCollectionRef(aircraftId)
            ?: return Result.failure(Exception("User not logged in"))
        saveCard(ref.document(card.id), card)
        Result.success(true)
    } catch (e: Exception) {
        logger.atWarning().withCause(e).log("Error updating inspection card ${card.id}")
        Result.failure(e)
    }

    override suspend fun deleteInspection(aircraftId: String, cardId: String): Result<Boolean> = try {
        val ref = getInspectionsCollectionRef(aircraftId)
            ?: return Result.failure(Exception("User not logged in"))
        ref.document(cardId).delete().await()
        logger.atFine().log("Inspection card $cardId deleted.")
        Result.success(true)
    } catch (e: Exception) {
        logger.atWarning().withCause(e).log("Error deleting inspection card $cardId")
        Result.failure(e)
    }

    override suspend fun computeNextDue(
        card: InspectionCard,
        logs: List<MaintenanceLog>,
    ): DueStatus {
        // Force overrides take precedence
        val forceDueDate = card.forceDueDate
        val forceDueTach = card.forceDueTach

        val hasForcedDate = forceDueDate != null && (forceDueDate.seconds > 0 || forceDueDate.nanos > 0)
        val hasForcedTach = forceDueTach > 0f

        if (hasForcedDate || hasForcedTach) {
            val nextDueDate = if (hasForcedDate) {
                Instant.ofEpochSecond(forceDueDate.seconds)
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
            .filter { card.id in it.inspectionIdsList }
            .sortedByDescending { it.timestamp.seconds }

        val today = LocalDate.now()
        var resultDate: LocalDate? = null
        var resultTach: Float? = null
        var isOnCondition = false

        for (rule in card.rulesList) {
            when (rule.ruleCase) {
                RuleCase.TIME_RULE -> {
                    val intervalMonths = rule.timeRule.intervalMonths.toLong()
                    val baseDate = if (associatedLogs.isNotEmpty()) {
                        Instant.ofEpochSecond(associatedLogs.first().timestamp.seconds)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    } else {
                        today
                    }
                    val candidate = baseDate.plusMonths(intervalMonths)
                    if (resultDate == null || candidate.isBefore(resultDate)) {
                        resultDate = candidate
                    }
                }

                RuleCase.TACH_RULE -> {
                    val intervalHours = rule.tachRule.intervalHours
                    val baseTach = if (associatedLogs.isNotEmpty()) {
                        associatedLogs.first().tachTime.toFloat()
                    } else {
                        0f
                    }
                    val candidate = baseTach + intervalHours
                    if (resultTach == null || candidate < resultTach!!) {
                        resultTach = candidate
                    }
                }

                RuleCase.ON_CONDITION_RULE -> {
                    isOnCondition = true
                }

                else -> { /* ignore RULE_NOT_SET */ }
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
            INSPECTION_CARD_BLOB to Blob.fromBytes(card.toByteArray()),
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
        private val logger: FluentLogger = FluentLogger.forEnclosingClass()
        private const val INSPECTIONS_COLLECTION = "inspections"
        private const val INSPECTION_CARD_BLOB = "inspection_card_blob"
        private const val TITLE_FIELD = "title"
        private const val COMPONENT_FIELD = "component"
    }
}
