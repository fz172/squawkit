package dev.fanfly.wingslog.feature.sharing.datamanager

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

private const val CODE = "EFA2GGTH"

class ThingShareDeepLinksTest {

  @After
  fun tearDown() = ThingShareDeepLinks.consume()

  @Test
  fun parses_a_code_link() {
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#$CODE"))
      .isEqualTo(ShareInvite(CODE))
  }

  @Test
  fun a_link_names_no_thing_and_no_host() {
    // The entire point of #164. A thing id in an invite is a capability: whoever holds one can
    // fabricate a same-id thing and read the victim's ACL and technician certificate numbers
    // (#202), or re-claim an abandoned share (#204). The code names nothing real.
    val invite =
      ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#$CODE")

    assertThat(invite?.code).isEqualTo(CODE)
    assertThat(ShareInvite::class.members.map { it.name }).doesNotContain("thingId")
    assertThat(ShareInvite::class.members.map { it.name }).doesNotContain("hostUid")
  }

  @Test
  fun accepts_the_displayed_grouping_and_lowercase() {
    // A link and a hand-typed code go down exactly one path, so "EFA2-GGTH" pasted from a text
    // message works as well as the raw code.
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#efa2-ggth"))
      .isEqualTo(ShareInvite(CODE))
  }

  @Test
  fun tolerates_query_params_and_a_trailing_slash() {
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share?utm=x#$CODE"))
      .isEqualTo(ShareInvite(CODE))
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share/#$CODE"))
      .isEqualTo(ShareInvite(CODE))
  }

  @Test
  fun rejects_legacy_links_rather_than_guessing_at_them() {
    // Pre-#164 links carried {thingId}.{secret} or {hostUid}.{thingId}.{secret}. The mechanism
    // they addressed is gone. Dots are not in the code alphabet, so they normalize to nothing.
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#ac-1.sEcReT")).isNull()
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#host.ac-1.sEcReT")).isNull()
  }

  @Test
  fun rejects_non_share_links_and_malformed_codes() {
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/finishSignIn?apiKey=x")).isNull()
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/other#$CODE")).isNull()
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share")).isNull()
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#")).isNull()
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#SHORT")).isNull()
    assertThat(ThingShareDeepLinks.parse("https://squawkit.fanfly.dev/share#TOOLONGCODE")).isNull()
  }

  @Test
  fun deliver_parks_a_share_link_and_ignores_others() {
    assertThat(ThingShareDeepLinks.deliver("https://squawkit.fanfly.dev/finishSignIn")).isFalse()
    assertThat(ThingShareDeepLinks.pendingInvite.value).isNull()

    assertThat(ThingShareDeepLinks.deliver("https://squawkit.fanfly.dev/share#$CODE")).isTrue()
    assertThat(ThingShareDeepLinks.pendingInvite.value).isEqualTo(
      ShareInvite(
        CODE
      )
    )

    ThingShareDeepLinks.consume()
    assertThat(ThingShareDeepLinks.pendingInvite.value).isNull()
  }

  @Test
  fun deliverCode_parks_a_typed_code_down_the_same_path() {
    // #209: a hand-typed code lands on the same channel a link fills, normalized the same way —
    // grouping and case included, so what the field shows and what parks agree. It parks as
    // auto-accept: typing the code was the consent, so the redeem flow skips the confirm dialog.
    assertThat(ThingShareDeepLinks.deliverCode("efa2-ggth")).isTrue()
    assertThat(ThingShareDeepLinks.pendingInvite.value)
      .isEqualTo(ShareInvite(CODE, autoAccept = true))
  }

  @Test
  fun deliverCode_refuses_a_malformed_code_and_parks_nothing() {
    // A refused code keeps the entry screen open rather than parking nonsense that would only
    // surface an error sheet.
    assertThat(ThingShareDeepLinks.deliverCode("SHORT")).isFalse()
    assertThat(ThingShareDeepLinks.pendingInvite.value).isNull()
  }
}
