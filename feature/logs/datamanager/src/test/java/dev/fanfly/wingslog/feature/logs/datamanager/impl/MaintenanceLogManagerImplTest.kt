package dev.fanfly.wingslog.feature.logs.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-456"

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
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

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

  // ---- observeLogs ----

  @Test
  fun observeLogs_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    var emittedList: List<MaintenanceLog>? = null
    manager.observeLogs(TEST_AIRCRAFT_ID).collect {
      emittedList = it
    }

    assertThat(emittedList).isEmpty()
  }
}
