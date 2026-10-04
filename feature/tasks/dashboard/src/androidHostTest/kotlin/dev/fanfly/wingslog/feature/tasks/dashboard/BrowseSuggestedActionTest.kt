package dev.fanfly.wingslog.feature.tasks.dashboard

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import org.junit.Test

class BrowseSuggestedActionTest {

  private val browse: () -> Unit = {}
  private val signIn: () -> Unit = {}

  @Test
  fun `a guest is asked to sign in rather than shown suggestions`() {
    assertThat(
      browseSuggestedAction(
        SuggestEntry.SignInRequired,
        browse,
        signIn
      )
    ).isSameInstanceAs(signIn)
  }

  @Test
  fun `everyone else opens the list, a Thing missing its make and model included`() {
    listOf(
      SuggestEntry.Hidden,
      SuggestEntry.Available,
      SuggestEntry.MissingIdentity(listOf("Model"))
    ).forEach {
      assertThat(browseSuggestedAction(it, browse, signIn)).isSameInstanceAs(
        browse
      )
    }
  }

  @Test
  fun `every template has a curated list to browse but the custom one`() {
    assertThat(hasCuratedList("airplane")).isTrue()
    assertThat(hasCuratedList("home")).isTrue()
    assertThat(hasCuratedList("custom")).isFalse()
    // Not known yet: nothing to offer until the template is.
    assertThat(hasCuratedList(null)).isFalse()
  }
}
