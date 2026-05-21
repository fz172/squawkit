package dev.fanfly.wingslog.feature.settings.featurelab

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

actual class FeatureLabBackendProbe actual constructor() {

  private val functions = FirebaseFunctions.getInstance("us-central1")

  actual suspend fun callHealthProbe(): String {
    val result = awaitTask(functions.getHttpsCallable("health_probe").call())
    val payload = result.data as? Map<*, *> ?: return "Unexpected response: ${result.data}"
    val status = payload["status"] ?: "unknown"
    val message = payload["message"] ?: "no message"
    val uid = payload["uid"] ?: "missing uid"
    val appId = payload["appId"] ?: "missing appId"
    return "status=$status, message=$message, uid=$uid, appId=$appId"
  }
}

private suspend fun <T> awaitTask(task: Task<T>): T =
  suspendCancellableCoroutine { continuation ->
    task.addOnCompleteListener { completedTask ->
      val exception = completedTask.exception
      when {
        completedTask.isSuccessful -> continuation.resume(completedTask.result)
        exception != null -> continuation.resumeWithException(exception)
        else -> continuation.resumeWithException(IllegalStateException("Task failed without exception"))
      }
    }
  }
