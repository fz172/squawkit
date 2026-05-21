package dev.fanfly.wingslog.feature.settings.featurelab

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Minimal callable-function probe for local Firebase Functions emulator verification.
 */
class FeatureLabBackendProbe(
  private val httpClient: HttpClient,
) {

  suspend fun callHealthProbe(): String =
    httpClient.post(healthProbeUrl()) {
      contentType(ContentType.Application.Json)
      setBody("""{"data":null}""")
    }
      .bodyAsText()
}

internal expect fun healthProbeUrl(): String
