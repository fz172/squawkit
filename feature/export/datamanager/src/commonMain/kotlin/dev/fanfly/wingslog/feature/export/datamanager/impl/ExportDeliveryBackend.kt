package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

class ExportDeliveryBackend {
  private val functions = Firebase.functions("us-central1")

  suspend fun requestExportDelivery(
    exportId: String,
    forceResend: Boolean = false,
  ): ExportDeliveryResult {
    val response = functions
      .httpsCallable("requestExportDelivery")
      .invoke(
        RequestExportDeliveryRequestData(
          exportId = exportId,
          forceResend = forceResend
        )
      )
      .data<RequestExportDeliveryResponseData>()
    return ExportDeliveryResult(
      deliveryState = response.deliveryState,
      deliverySentAtEpochMillis = response.deliverySentAtEpochMillis,
      deliveryFailureCode = response.deliveryFailureCode,
      deliveryFailureMessage = response.deliveryFailureMessage,
    )
  }
}

/**
 * Encodes the callable request. Sent as a serializable type rather than a raw map so the boolean
 * survives encoding — a `Map<String, Any>` would fail because the `Any` value type has no serializer.
 */
@Serializable
private data class RequestExportDeliveryRequestData(
  val exportId: String,
  val forceResend: Boolean = false,
)

data class ExportDeliveryResult(
  val deliveryState: String,
  val deliverySentAtEpochMillis: Long = 0L,
  val deliveryFailureCode: String = "",
  val deliveryFailureMessage: String = "",
)

/**
 * Decodes the callable response. The response is read through GitLive's serializer-backed
 * `data<T>()`, so a `Map<String, Any?>` can't be used (the `Any` value type has no serializer).
 * Every field is defaulted so a missing key falls back instead of failing to decode.
 */
@Serializable
private data class RequestExportDeliveryResponseData(
  val status: String = "",
  val exportId: String = "",
  val uid: String = "",
  val appId: String = "",
  val deliveryState: String = "",
  val deliverySentAtEpochMillis: Long = 0L,
  val deliveryFailureCode: String = "",
  val deliveryFailureMessage: String = "",
)
