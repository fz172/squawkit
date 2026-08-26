package dev.fanfly.wingslog.feature.notifications.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import org.junit.Test

/**
 * The property the inverted-boolean convention (design §4.1) exists for: a default-constructed
 * [NotificationSettings] — same shape as a document that was never written, since proto3 has no
 * scalar field presence — must read every class as ON. This is also the contract the server-side
 * fan-out trigger independently relies on (design §7.4) without decoding through this module at all.
 */
class NotificationSettingsExtTest {

  @Test
  fun absentDoc_resolvesToAllOn() {
    val settings = NotificationSettings()

    assertThat(settings.allEnabled).isTrue()
    assertThat(settings.squawkPriorityEnabled).isTrue()
    assertThat(settings.overdueEnabled).isTrue()
    assertThat(settings.dueSoonEnabled).isTrue()
    assertThat(settings.aircraftActivityEnabled).isTrue()
    assertThat(settings.squawkActivityEnabled).isTrue()
    assertThat(settings.taskActivityEnabled).isTrue()
    assertThat(settings.logActivityEnabled).isTrue()
  }

  @Test
  fun disabledField_readsAsFalse() {
    val settings = NotificationSettings(squawk_priority_disabled = true)

    assertThat(settings.squawkPriorityEnabled).isFalse()
    // Every sibling field is untouched — the inversion is per-field, not all-or-nothing.
    assertThat(settings.overdueEnabled).isTrue()
  }

  @Test
  fun withAllEnabled_false_setsMasterSwitchOnly() {
    val settings = NotificationSettings().withAllEnabled(enabled = false)

    assertThat(settings.allEnabled).isFalse()
    // Per-class fields are untouched by the master switch — NotificationPrefsManager's consumers
    // read allEnabled as a short-circuit, not as something that rewrites every other field.
    assertThat(settings.squawkPriorityEnabled).isTrue()
  }

  @Test
  fun withSquawkPriority_flipsOnlyThatField() {
    val settings = NotificationSettings().withSquawkPriority(enabled = false)

    assertThat(settings.squawkPriorityEnabled).isFalse()
    assertThat(settings.overdueEnabled).isTrue()
  }

  @Test
  fun withOverdue_flipsOnlyThatField() {
    val settings = NotificationSettings().withOverdue(enabled = false)

    assertThat(settings.overdueEnabled).isFalse()
    assertThat(settings.dueSoonEnabled).isTrue()
  }

  @Test
  fun withDueSoon_flipsOnlyThatField() {
    val settings = NotificationSettings().withDueSoon(enabled = false)

    assertThat(settings.dueSoonEnabled).isFalse()
    assertThat(settings.overdueEnabled).isTrue()
  }

  @Test
  fun withAircraftActivity_flipsOnlyThatField() {
    val settings = NotificationSettings().withAircraftActivity(enabled = false)

    assertThat(settings.aircraftActivityEnabled).isFalse()
    assertThat(settings.squawkActivityEnabled).isTrue()
  }

  @Test
  fun withSquawkActivity_flipsOnlyThatField() {
    val settings = NotificationSettings().withSquawkActivity(enabled = false)

    assertThat(settings.squawkActivityEnabled).isFalse()
    assertThat(settings.taskActivityEnabled).isTrue()
  }

  @Test
  fun withTaskActivity_flipsOnlyThatField() {
    val settings = NotificationSettings().withTaskActivity(enabled = false)

    assertThat(settings.taskActivityEnabled).isFalse()
    assertThat(settings.logActivityEnabled).isTrue()
  }

  @Test
  fun withLogActivity_flipsOnlyThatField() {
    val settings = NotificationSettings().withLogActivity(enabled = false)

    assertThat(settings.logActivityEnabled).isFalse()
    assertThat(settings.aircraftActivityEnabled).isTrue()
  }
}
