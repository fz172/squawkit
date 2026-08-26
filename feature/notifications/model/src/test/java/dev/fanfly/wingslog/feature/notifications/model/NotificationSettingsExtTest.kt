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
    assertThat(settings.priorityDueEnabled).isTrue()
    assertThat(settings.collaborationEnabled).isTrue()
  }

  @Test
  fun disabledField_readsAsFalse() {
    val settings = NotificationSettings(priority_due_disabled = true)

    assertThat(settings.priorityDueEnabled).isFalse()
    // The sibling field is untouched — the inversion is per-field, not all-or-nothing.
    assertThat(settings.collaborationEnabled).isTrue()
  }

  @Test
  fun withAllEnabled_false_setsMasterSwitchOnly() {
    val settings = NotificationSettings().withAllEnabled(enabled = false)

    assertThat(settings.allEnabled).isFalse()
    // Per-class fields are untouched by the master switch — NotificationPrefsManager's consumers
    // read allEnabled as a short-circuit, not as something that rewrites every other field.
    assertThat(settings.priorityDueEnabled).isTrue()
  }

  @Test
  fun withPriorityDue_flipsOnlyThatField() {
    val settings = NotificationSettings().withPriorityDue(enabled = false)

    assertThat(settings.priorityDueEnabled).isFalse()
    assertThat(settings.collaborationEnabled).isTrue()
  }

  @Test
  fun withCollaboration_flipsOnlyThatField() {
    val settings = NotificationSettings().withCollaboration(enabled = false)

    assertThat(settings.collaborationEnabled).isFalse()
    assertThat(settings.priorityDueEnabled).isTrue()
  }
}
