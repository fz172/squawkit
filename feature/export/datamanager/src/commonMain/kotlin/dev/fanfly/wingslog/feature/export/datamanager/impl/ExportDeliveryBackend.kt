package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.functions.httpsCallable
import kotlinx.serialization.Serializable

class ExportDeliveryBackend {
  private val functions = Firebase.functions("us-central1")

  suspend fun requestExportDelivery(exportId: String): ExportDeliveryResult {
    val response = functions
      .httpsCallable("requestExportDelivery")
      .invoke(RequestExportDeliveryPayload(exportId))
      .data<RequestExportDeliveryPayloadResponse>()
    return ExportDeliveryResult(
      deliveryState = response.deliveryState,
      deliverySentAtEpochMillis = response.deliverySentAtEpochMillis,
      deliveryFailureCode = response.deliveryFailureCode.orEmpty(),
      deliveryFailureMessage = response.deliveryFailureMessage.orEmpty(),
    )
  }
}

data class ExportDeliveryResult(
  val deliveryState: String,
  val deliverySentAtEpochMillis: Long = 0L,
  val deliveryFailureCode: String = "",
  val deliveryFailureMessage: String = "",
)

@Serializable
private data class RequestExportDeliveryPayload(
  val exportId: String,
)

@Serializable
private data class RequestExportDeliveryPayloadResponse(
  val status: String,
  val exportId: String,
  val uid: String,
  val appId: String,
  val deliveryState: String,
  val deliverySentAtEpochMillis: Long = 0L,
  val deliveryFailureCode: String? = null,
  val deliveryFailureMessage: String? = null,
)
