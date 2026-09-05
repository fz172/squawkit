package dev.fanfly.wingslog.feature.technician.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.Technician
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class SelfDisplayNameTest {

  private fun user(displayName: String?, email: String?): FirebaseUser =
    mockk {
      every { this@mockk.displayName } returns displayName
      every { this@mockk.email } returns email
    }

  @Test
  fun `in-app technician name wins over the account name`() {
    val self = Technician(id = "t1", name = "Sponge Bob")
    assertThat(selfDisplayName(self, user("Jordan Reyes", "jordan@example.com")))
      .isEqualTo("Sponge Bob")
  }

  @Test
  fun `blank technician name falls back to the account display name`() {
    val self = Technician(id = "t1", name = "  ")
    assertThat(selfDisplayName(self, user("Jordan Reyes", "jordan@example.com")))
      .isEqualTo("Jordan Reyes")
  }

  @Test
  fun `missing technician falls back to the account display name`() {
    assertThat(selfDisplayName(null, user("Jordan Reyes", "jordan@example.com")))
      .isEqualTo("Jordan Reyes")
  }

  @Test
  fun `blank account name falls back to the email`() {
    assertThat(selfDisplayName(null, user("", "jordan@example.com")))
      .isEqualTo("jordan@example.com")
  }

  @Test
  fun `nothing set resolves to null`() {
    assertThat(selfDisplayName(null, user(null, null))).isNull()
    assertThat(selfDisplayName(null, null)).isNull()
  }
}
