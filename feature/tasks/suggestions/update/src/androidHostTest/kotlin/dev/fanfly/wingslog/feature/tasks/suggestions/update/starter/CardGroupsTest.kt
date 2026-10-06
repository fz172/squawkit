package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.tasks.suggestions.model.StarterPackItem
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import org.junit.Test

class CardGroupsTest {

  private fun card(title: String, slot: String) =
    StarterPackItem(
      suggestion = TaskSuggestion(
        title = title,
        component_slot_key = slot
      ), selected = false
    )

  @Test
  fun `the thing's own section leads, then components in the order each first appears`() {
    val items = listOf(
      card("Spark plugs", "engine"),
      card("Annual", ""),
      card("Oil", "engine"),
      card("Prop", "propeller")
    )

    val groups = groupsOf(items)

    assertThat(groups.map { it.slotKey }).containsExactly(
      "",
      "engine",
      "propeller"
    )
      .inOrder()
    assertThat(groups[1].cards.map { it.index }).containsExactly(0, 2)
      .inOrder()
    assertThat(groups[0].cards.single().value.suggestion.title).isEqualTo("Annual")
  }

  @Test
  fun `one component is one group`() {
    assertThat(groupsOf(listOf(card("A", ""), card("B", "")))).hasSize(1)
    assertThat(groupsOf(emptyList())).isEmpty()
  }
}
