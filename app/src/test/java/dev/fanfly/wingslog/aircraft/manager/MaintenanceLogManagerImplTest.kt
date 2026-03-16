package dev.fanfly.wingslog.aircraft.manager

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.AggregateQuery
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.manager.impl.MaintenanceLogManagerImpl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

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

    private val testUserId = "test-user-123"
    private val testAircraftId = "aircraft-456"
    private val testLogId = "log-789"

    @Before
    fun setUp() {
        firebaseAuth = mockk(relaxed = true)
        firestore = mockk(relaxed = true)

        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns testUserId
        every { firebaseAuth.currentUser } returns mockUser

        // Set up the Firestore chain
        usersCollection = mockk(relaxed = true)
        userDocument = mockk(relaxed = true)
        fleetCollection = mockk(relaxed = true)
        aircraftDocument = mockk(relaxed = true)
        logsCollection = mockk(relaxed = true)

        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document(testUserId) } returns userDocument
        every { userDocument.collection("fleet") } returns fleetCollection
        every { fleetCollection.document(testAircraftId) } returns aircraftDocument
        every { aircraftDocument.collection("maintenance_logs") } returns logsCollection

        manager = MaintenanceLogManagerImpl(firebaseAuth, firestore)
    }

    private fun buildTestLog(id: String = testLogId): MaintenanceLog {
        return MaintenanceLog.newBuilder()
            .setId(id)
            .setWorkDescription("Oil change performed")
            .setComponentType(MaintenanceLog.ComponentType.ENGINE)
            .setComponentSerial("ENG-001")
            .setTachTime(1234.5)
            .setTimestamp(com.google.protobuf.Timestamp.newBuilder().setSeconds(1700000000L).build())
            .build()
    }

    // ---- addLog ----

    @Test
    fun `addLog success case`() = runTest {
        val log = buildTestLog()
        val docRef = mockk<DocumentReference>(relaxed = true)
        val setTask = Tasks.forResult<Void>(null)

        every { logsCollection.document(testLogId) } returns docRef
        every { docRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns setTask

        val result = manager.addLog(testAircraftId, log)

        assertTrue("addLog should succeed", result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `addLog when user not logged in returns failure`() = runTest {
        every { firebaseAuth.currentUser } returns null

        val log = buildTestLog()
        val result = manager.addLog(testAircraftId, log)

        assertTrue("addLog should fail when user not logged in", result.isFailure)
        assertEquals("User not logged in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `addLog with empty id generates new document reference`() = runTest {
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

        val result = manager.addLog(testAircraftId, logWithEmptyId)

        assertTrue("addLog with empty id should succeed", result.isSuccess)
    }

    // ---- updateLog ----

    @Test
    fun `updateLog success`() = runTest {
        val log = buildTestLog()
        val docRef = mockk<DocumentReference>(relaxed = true)
        val setTask = Tasks.forResult<Void>(null)

        every { logsCollection.document(testLogId) } returns docRef
        every { docRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns setTask

        val result = manager.updateLog(testAircraftId, log)

        assertTrue("updateLog should succeed", result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `updateLog when user not logged in returns failure`() = runTest {
        every { firebaseAuth.currentUser } returns null

        val log = buildTestLog()
        val result = manager.updateLog(testAircraftId, log)

        assertTrue("updateLog should fail when user not logged in", result.isFailure)
    }

    // ---- deleteLog ----

    @Test
    fun `deleteLog success`() = runTest {
        val docRef = mockk<DocumentReference>(relaxed = true)
        val deleteTask = Tasks.forResult<Void>(null)

        every { logsCollection.document(testLogId) } returns docRef
        every { docRef.delete() } returns deleteTask

        val result = manager.deleteLog(testAircraftId, testLogId)

        assertTrue("deleteLog should succeed", result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `deleteLog when user not logged in returns failure`() = runTest {
        every { firebaseAuth.currentUser } returns null

        val result = manager.deleteLog(testAircraftId, testLogId)

        assertTrue("deleteLog should fail when user not logged in", result.isFailure)
    }

    // ---- getRecentLogCount ----

    @Test
    fun `getRecentLogCount success with non-zero count`() = runTest {
        val query = mockk<Query>(relaxed = true)
        val aggregateQuery = mockk<AggregateQuery>(relaxed = true)
        val aggregateSnapshot = mockk<AggregateQuerySnapshot>(relaxed = true)
        val aggregateTask = Tasks.forResult(aggregateSnapshot)

        every { logsCollection.whereGreaterThan(any<String>(), any()) } returns query
        every { query.count() } returns aggregateQuery
        every { aggregateQuery.get(AggregateSource.SERVER) } returns aggregateTask
        every { aggregateSnapshot.count } returns 7L

        val result = manager.getRecentLogCount(testAircraftId, days = 30)

        assertTrue("getRecentLogCount should succeed", result.isSuccess)
        assertEquals(7L, result.getOrNull())
    }

    @Test
    fun `getRecentLogCount when user not logged in returns failure`() = runTest {
        every { firebaseAuth.currentUser } returns null

        val result = manager.getRecentLogCount(testAircraftId, days = 30)

        assertTrue("getRecentLogCount should fail when user not logged in", result.isFailure)
    }
}
