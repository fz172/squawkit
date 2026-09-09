package dev.fanfly.wingslog.core.analytics

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.crash.CrashReporter
import org.junit.Test

class AnalyticsPreferenceControllerTest {
  private val store = InMemoryStore()
  private val analytics = RecordingAnalytics()
  private val crash = RecordingCrash()

  @Test
  fun `the stored preference is applied to both sinks at construction`() {
    store.enabled = false

    AnalyticsPreferenceController(store, analytics, crash)

    assertThat(analytics.enabled).isFalse()
    assertThat(crash.enabled).isFalse()
  }

  @Test
  fun `turning diagnostics off stops analytics and crash collection together`() {
    val controller = AnalyticsPreferenceController(store, analytics, crash)

    controller.setEnabled(false)

    assertThat(store.enabled).isFalse()
    assertThat(analytics.enabled).isFalse()
    assertThat(crash.enabled).isFalse()
  }

  private class InMemoryStore(var enabled: Boolean = true) : AnalyticsPreferenceStore {
    override fun load(): Boolean = enabled

    override fun save(enabled: Boolean) {
      this.enabled = enabled
    }
  }

  private class RecordingAnalytics : AnalyticsManager {
    var enabled: Boolean? = null

    override fun logScreenView(screenName: String, params: Map<String, String>) = Unit

    override fun logEvent(name: String, params: Map<String, String>) = Unit

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
      this.enabled = enabled
    }
  }

  private class RecordingCrash : CrashReporter {
    var enabled: Boolean? = null

    override fun recordException(throwable: Throwable) = Unit

    override fun log(message: String) = Unit

    override fun setUserId(userId: String?) = Unit

    override fun setCustomKey(key: String, value: String) = Unit

    override fun setCrashCollectionEnabled(enabled: Boolean) {
      this.enabled = enabled
    }
  }
}
