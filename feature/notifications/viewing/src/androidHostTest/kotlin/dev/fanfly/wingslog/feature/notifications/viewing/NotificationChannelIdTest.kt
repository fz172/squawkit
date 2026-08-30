package dev.fanfly.wingslog.feature.notifications.viewing

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import org.junit.Test

/**
 * Pins the OS notification channel ids as literals (#663).
 *
 * **Renaming a channel id silently drops every user's per-channel settings.** Android keys
 * importance, sound, and whether the channel is blocked at all by this string; a rename creates a
 * new channel at the default importance and orphans the old one. A user who had turned collaboration
 * pushes down to silent starts getting them at full volume, with no error and no migration path.
 *
 * Nothing else can catch it. The id never renders, so the byte-identical snapshot test (#658) cannot
 * see it; the display name beside it still looks right, so review does not either. The precedent is
 * `CollectionKindCoverageTest`, which pins wire names for the same reason — same class of bug, and
 * this one has the worse blast radius because the damage lands on settings the user chose by hand.
 *
 * **The issue asked for `GROUNDED` and that channel no longer exists.** AOG stopped being its own
 * tier on 2026-08-26 and reports through `URGENCY_UPDATE` like any other escalation, so the two
 * channels below are the whole set. The reason for pinning is unchanged.
 */
class NotificationChannelIdTest {

  @Test
  fun channelIdsAreExactlyWhatShipped() {
    assertThat(NotificationChannel.COLLABORATION.channelId()).isEqualTo("collaboration")
    assertThat(NotificationChannel.URGENCY_UPDATE.channelId()).isEqualTo("urgency_update")
  }

  @Test
  fun everyChannelHasADistinctId() {
    // Two channels sharing an id is the same damage arriving differently: one set of user settings
    // silently governs both, and the importance of whichever registered last wins.
    val ids = NotificationChannel.entries.map { it.channelId() }
    assertThat(ids).containsNoDuplicates()
    assertThat(ids).hasSize(NotificationChannel.entries.size)
  }

  @Test
  fun theChannelIdIsIndependentOfAnyLexiconWord() {
    // The display name resolves from Lexicon.down_status and will differ per template (#662); the
    // id must not follow it. Asserted as a literal with no lexicon in sight, which is the point.
    assertThat(NotificationChannel.URGENCY_UPDATE.channelId()).doesNotContain("AOG")
    assertThat(NotificationChannel.URGENCY_UPDATE.channelId()).doesNotContain("aircraft")
  }
}
