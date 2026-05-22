package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.functions.httpsCallable
import dev.fanfly.wingslog.rpc.requestexportdelivery.RequestExportDeliveryRequest
import dev.fanfly.wingslog.rpc.requestexportdelivery.RequestExportDeliveryResponse

class ExportDeliveryBackend {
  private val functions = Firebase.functions("us-central1")

  suspend fun requestExportDelivery(exportId: String): ExportDeliveryResult {
    val request = RequestExportDeliveryRequest(export_id = exportId)
    val response = functions
      .httpsCallable("requestExportDelivery")
      .invoke(mapOf("exportId" to request.export_id))
      .data<Map<String, Any?>>()
      .toProto()
    return ExportDeliveryResult(
      deliveryState = response.delivery_state,
      deliverySentAtEpochMillis = response.delivery_sent_at_epoch_millis,
      deliveryFailureCode = response.delivery_failure_code,
      deliveryFailureMessage = response.delivery_failure_message,
    )
  }
}

data class ExportDeliveryResult(
  val deliveryState: String,
  val deliverySentAtEpochMillis: Long = 0L,
  val deliveryFailureCode: String = "",
  val deliveryFailureMessage: String = "",
)

private fun Map<String, Any?>.toProto() = RequestExportDeliveryResponse(
  status = string("status"),
  export_id = string("exportId"),
  uid = string("uid"),
  app_id = string("appId"),
  delivery_state = string("deliveryState"),
  delivery_sent_at_epoch_millis = long("deliverySentAtEpochMillis"),
  delivery_failure_code = string("deliveryFailureCode"),
  delivery_failure_message = string("deliveryFailureMessage"),
)

private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""

private fun Map<String, Any?>.long(key: String): Long = when (val value = this[key]) {
  is Long -> value
  is Int -> value.toLong()
  is Double -> value.toLong()
  else -> 0L
}
