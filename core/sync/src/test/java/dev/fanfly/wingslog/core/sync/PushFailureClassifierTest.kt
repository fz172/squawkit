package dev.fanfly.wingslog.core.sync

import com.google.common.truth.Truth.assertThat
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class PushFailureClassifierTest {

  // --- Auth-class ---

  @Test
  fun classifyPushFailure_firebaseAuthException_returnsAuthExpired() {
    val e = mockk<FirebaseAuthException>(relaxed = true)

    val result = classifyPushFailure(e)

    assertThat(result).isInstanceOf(SyncFailure.AuthExpired::class.java)
  }

  @Test
  fun classifyPushFailure_firestorePermissionDenied_returnsAuthExpired() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.PERMISSION_DENIED

    val result = classifyPushFailure(e)

    assertThat(result).isInstanceOf(SyncFailure.AuthExpired::class.java)
  }

  @Test
  fun classifyPushFailure_firestoreUnauthenticated_returnsAuthExpired() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.UNAUTHENTICATED

    val result = classifyPushFailure(e)

    assertThat(result).isInstanceOf(SyncFailure.AuthExpired::class.java)
  }

  // --- Transient (null) ---

  @Test
  fun classifyPushFailure_firestoreUnavailable_returnsNull() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.UNAVAILABLE

    assertThat(classifyPushFailure(e)).isNull()
  }

  @Test
  fun classifyPushFailure_firestoreDeadlineExceeded_returnsNull() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.DEADLINE_EXCEEDED

    assertThat(classifyPushFailure(e)).isNull()
  }

  @Test
  fun classifyPushFailure_firestoreInternal_returnsNull() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.INTERNAL

    assertThat(classifyPushFailure(e)).isNull()
  }

  @Test
  fun classifyPushFailure_firestoreResourceExhausted_returnsNull() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.RESOURCE_EXHAUSTED

    assertThat(classifyPushFailure(e)).isNull()
  }

  @Test
  fun classifyPushFailure_firestoreAborted_returnsNull() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.ABORTED

    assertThat(classifyPushFailure(e)).isNull()
  }

  @Test
  fun classifyPushFailure_firestoreCancelled_returnsNull() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.CANCELLED

    assertThat(classifyPushFailure(e)).isNull()
  }

  // --- Other → SyncFailure.Push ---

  @Test
  fun classifyPushFailure_firestoreInvalidArgument_returnsPushWithSyncErrorPrefix() {
    val e = mockk<FirebaseFirestoreException>()
    every { e.code } returns FirestoreExceptionCode.INVALID_ARGUMENT
    every { e.message } returns "bad field"

    val result = classifyPushFailure(e)

    assertThat(result).isInstanceOf(SyncFailure.Push::class.java)
    assertThat((result as SyncFailure.Push).message).contains("Sync error")
  }

  @Test
  fun classifyPushFailure_plainIllegalStateException_returnsPushContainingMessage() {
    val e = IllegalStateException("boom")

    val result = classifyPushFailure(e)

    assertThat(result).isInstanceOf(SyncFailure.Push::class.java)
    assertThat((result as SyncFailure.Push).message).contains("boom")
  }

  @Test
  fun classifyPushFailure_throwableWithNullMessage_returnsPushWithNonEmptyMessage() {
    val e = RuntimeException()

    val result = classifyPushFailure(e)

    assertThat(result).isInstanceOf(SyncFailure.Push::class.java)
    assertThat((result as SyncFailure.Push).message).isNotEmpty()
  }
}
