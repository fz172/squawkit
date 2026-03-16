package dev.fanfly.wingslog.aircraft.manager

import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.AggregateQuery
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.manager.impl.MaintenanceLogManagerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-456"
private const val TEST_LOG_ID = "log-789"

class MaintenanceLogManagerImplTest {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var manager: MaintenanceLogManagerImpl

    // Mock chain: firestore -> users/{uid}/fleet -> {aircraftId}/maintenance_logs
    private lateinit var usersCollection: CollectionReference
    private lateinit var userDocument: DocumentReference
    private lateinit var fleetCollection: CollectionReference
    private lateinit var aircraftDocument: DocumentReference
    private lateinit var logsCollection: CollectionReference

    @Before
    fun setUp() {
        firebaseAuth = mockk(relaxed = true)
        firestore = mockk(relaxed = true)

        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns TEST_USER_ID
        every { firebaseAuth.currentUser } returns mockUser

        // Set up the Firestore chain
        usersCollection = mockk(relaxed = true)
        userDocument = mockk(relaxed = true)
        fleetCollection = mockk(relaxed = true)
        aircraftDocument = mockk(relaxed = true)
        logsCollection = mockk(relaxed = true)

        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document(TEST_USER_ID) } returns userDocument
        every { userDocument.collection("fleet") } returns fleetCollection
        every { fleetCollection.document(TEST_AIRCRAFT_ID) } returns aircraftDocument
        every { aircraftDocument.collection("maintenance_logs") } returns logsCollection

        manager = MaintenanceLogManagerImpl(firebaseAuth, firestore)
    }

    private fun buildTestLog(id: String = TEST_LOG_ID): MaintenanceLog {
        return MaintenanceLog.newBuilder()
            .setId(id)
            .setWorkDescription("Oil change performed")
            .setComponentType(MaintenanceLog.ComponentType.ENGINE)
            .setComponentSerial("ENG-001")
            .setTachTime(1234.5)
            .setTimestamp(com.google.protobuf.Timestamp.newBuilder().setSeconds(1700000000L).build())
            .build()
    }

    // ---- observeLogs ----

    @Test
    fun observeLogs_withoutLoggedInUser_emitsError() = runTest {
        every { firebaseAuth.currentUser } returns null

        val result = runCatching {
            manager.observeLogs(TEST_AIRCRAFT_ID).collect()
        }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Logs reference is null")
    }

    // ---- addLog ----

    @Test
    fun addLog_withValidLog_returnsSuccess() = runTest {
        val log = buildTestLog()
        val docRef = mockk<DocumentReference>(relaxed = true)
        val setTask = Tasks.forResult<Void>(null)

        every { logsCollection.document(TEST_LOG_ID) } returns docRef
        every { docRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns setTask

        val result = manager.addLog(TEST_AIRCRAFT_ID, log)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun addLog_withoutLoggedInUser_returnsFailure() = runTest {
        every { firebaseAuth.currentUser } returns null

        val log = buildTestLog()
        val result = manager.addLog(TEST_AIRCRAFT_ID, log)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("User not logged in")
    }

    @Test
    fun addLog_withEmptyId_generatesNewDocumentReference() = runTest {
        val logWithEmptyId = MaintenanceLog.newBuilder()
            .setId("")
            .setWorkDescription("Annual inspection")
            .build()

        val generatedDocRef = mockk<DocumentReference>(relaxed = true)
        val generatedId = UUID.randomUUID().toString()
        every { generatedDocRef.id } returns generatedId
        every { logsCollection.document() } returns generatedDocRef
        val setTask = Tasks.forResult<Void>(null)
        every { generatedDocRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns setTask

        val result = manager.addLog(TEST_AIRCRAFT_ID, logWithEmptyId)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun addLog_whenFirestoreFails_returnsFailure() = runTest {
        val log = buildTestLog()
        val docRef = mockk<DocumentReference>(relaxed = true)
        val exception = Exception("Firestore error")
        val setTask = Tasks.forException<Void>(exception)

        every { logsCollection.document(TEST_LOG_ID) } returns docRef
        every { docRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns setTask

        val result = manager.addLog(TEST_AIRCRAFT_ID, log)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Firestore error")
    }

    @Test
    fun addLog_mapsDataCorrectly() = runTest {
        val log = buildTestLog()
        val docRef = mockk<DocumentReference>(relaxed = true)
        val setTask = Tasks.forResult<Void>(null)

        every { logsCollection.document(TEST_LOG_ID) } returns docRef
        
        val dataSlot = slot<Map<String, Any>>()
        every { docRef.set(capture(dataSlot), any<SetOptions>()) } returns setTask

        manager.addLog(TEST_AIRCRAFT_ID, log)

        val capturedData = dataSlot.captured
        assertThat(capturedData["component_type"]).isEqualTo(log.componentType.name)
        assertThat(capturedData["component_serial"]).isEqualTo(log.componentSerial)
        assertThat(capturedData["tach_time"]).isEqualTo(log.tachTime)
        assertThat(capturedData["log_info_blob"]).isNotNull()
    }

    // ---- updateLog ----

    @Test
    fun updateLog_withValidLog_returnsSuccess() = runTest {
        val log = buildTestLog()
        val docRef = mockk<DocumentReference>(relaxed = true)
        val setTask = Tasks.forResult<Void>(null)

        every { logsCollection.document(TEST_LOG_ID) } returns docRef
        every { docRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns setTask

        val result = manager.updateLog(TEST_AIRCRAFT_ID, log)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun updateLog_withoutLoggedInUser_returnsFailure() = runTest {
        every { firebaseAuth.currentUser } returns null

        val log = buildTestLog()
        val result = manager.updateLog(TEST_AIRCRAFT_ID, log)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("User not logged in")
    }

    @Test
    fun updateLog_whenFirestoreFails_returnsFailure() = runTest {
        val log = buildTestLog()
        val docRef = mockk<DocumentReference>(relaxed = true)
        val exception = Exception("Firestore update error")
        val setTask = Tasks.forException<Void>(exception)

        every { logsCollection.document(TEST_LOG_ID) } returns docRef
        every { docRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns setTask

        val result = manager.updateLog(TEST_AIRCRAFT_ID, log)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Firestore update error")
    }

    // ---- deleteLog ----

    @Test
    fun deleteLog_withValidId_returnsSuccess() = runTest {
        val docRef = mockk<DocumentReference>(relaxed = true)
        val deleteTask = Tasks.forResult<Void>(null)

        every { logsCollection.document(TEST_LOG_ID) } returns docRef
        every { docRef.delete() } returns deleteTask

        val result = manager.deleteLog(TEST_AIRCRAFT_ID, TEST_LOG_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun deleteLog_withoutLoggedInUser_returnsFailure() = runTest {
        every { firebaseAuth.currentUser } returns null

        val result = manager.deleteLog(TEST_AIRCRAFT_ID, TEST_LOG_ID)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("User not logged in")
    }

    @Test
    fun deleteLog_whenFirestoreFails_returnsFailure() = runTest {
        val docRef = mockk<DocumentReference>(relaxed = true)
        val exception = Exception("Firestore delete error")
        val deleteTask = Tasks.forException<Void>(exception)

        every { logsCollection.document(TEST_LOG_ID) } returns docRef
        every { docRef.delete() } returns deleteTask

        val result = manager.deleteLog(TEST_AIRCRAFT_ID, TEST_LOG_ID)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Firestore delete error")
    }

    // ---- getRecentLogCount ----

    @Test
    fun getRecentLogCount_returnsCorrectCount() = runTest {
        val query = mockk<Query>(relaxed = true)
        val aggregateQuery = mockk<AggregateQuery>(relaxed = true)
        val aggregateSnapshot = mockk<AggregateQuerySnapshot>(relaxed = true)
        val aggregateTask = Tasks.forResult(aggregateSnapshot)

        every { logsCollection.whereGreaterThan(any<String>(), any()) } returns query
        every { query.count() } returns aggregateQuery
        every { aggregateQuery.get(AggregateSource.SERVER) } returns aggregateTask
        every { aggregateSnapshot.count } returns 7L

        val result = manager.getRecentLogCount(TEST_AIRCRAFT_ID, days = 30)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(7L)
    }

    @Test
    fun getRecentLogCount_withoutLoggedInUser_returnsFailure() = runTest {
        every { firebaseAuth.currentUser } returns null

        val result = manager.getRecentLogCount(TEST_AIRCRAFT_ID, days = 30)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("User not logged in")
    }
    @Test
    fun getRecentLogCount_whenFirestoreFails_returnsFailure() = runTest {
        val query = mockk<Query>(relaxed = true)
        val aggregateQuery = mockk<AggregateQuery>(relaxed = true)
        val exception = Exception("Firestore count error")
        val aggregateTask = Tasks.forException<AggregateQuerySnapshot>(exception)

        every { logsCollection.whereGreaterThan(any<String>(), any()) } returns query
        every { query.count() } returns aggregateQuery
        every { aggregateQuery.get(AggregateSource.SERVER) } returns aggregateTask

        val result = manager.getRecentLogCount(TEST_AIRCRAFT_ID, days = 30)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Firestore count error")
    }
}
