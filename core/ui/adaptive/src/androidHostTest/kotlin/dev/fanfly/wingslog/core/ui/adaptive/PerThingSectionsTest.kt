package dev.fanfly.wingslog.core.ui.adaptive

import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.Section
import org.junit.Test

/**
 * That the shell's navigation actually follows the template.
 *
 * **With the airplane set this gate is indistinguishable from no gate at all** — every section is
 * declared, in the shipped order, so a `perThingSections` that ignored its argument would render
 * identical navigation. Nothing on screen, and no test that only exercises the shipped template,
 * could tell the two apart. These cases exist to tell them apart.
 */
class PerThingSectionsTest {

  @Test
  fun theFailOpenDefaultRemovesNothing() {
    // #660: the failure this catches is a capability read with the *wrong default* — before the
    // registry resolves, outside a provider, or from a template that declares nothing — silently
    // removing a section for aviation users. A missing tab is far less noticeable in review than a
    // wrong word, so the default is asserted to be as harmless as the airplane set.
    assertThat(perThingSectionsFor(CurrentThingTemplate.ALL_ENABLED)).containsExactly(
      ShellSection.DASHBOARD,
      ShellSection.SQUAWKS,
      ShellSection.TASKS,
      ShellSection.LOGS,
    ).inOrder()
  }

  @Test
  fun aTemplateThatDeclaresFewerSectionsGetsFewerTabs() {
    val sections = perThingSectionsFor(
      Capabilities(
        sections = listOf(
          Section.SECTION_DASHBOARD,
          Section.SECTION_LOGS
        )
      ),
    )

    assertThat(sections).containsExactly(
      ShellSection.DASHBOARD,
      ShellSection.LOGS
    )
      .inOrder()
    assertThat(sections).doesNotContain(ShellSection.SQUAWKS)
  }

  @Test
  fun theDeclaredOrderIsTheRenderedOrder() {
    // The reason this is a list and not a bool per section: a set of flags cannot express order.
    val sections = perThingSectionsFor(
      Capabilities(
        sections = listOf(
          Section.SECTION_LOGS,
          Section.SECTION_DASHBOARD
        )
      ),
    )

    assertThat(sections).containsExactly(
      ShellSection.LOGS,
      ShellSection.DASHBOARD
    )
      .inOrder()
  }

  @Test
  fun aSectionThisBuildCannotRenderIsDropped() {
    // A template written by a newer client. Rendering the tab would navigate nowhere.
    val sections = perThingSectionsFor(
      Capabilities(
        sections = listOf(
          Section.SECTION_DASHBOARD,
          Section.SECTION_UNKNOWN
        )
      ),
    )

    assertThat(sections).containsExactly(ShellSection.DASHBOARD)
  }

  @Test
  fun declaringNothingFailsOpenRatherThanRemovingAllNavigation() {
    // Fail open: a template with no sections is a broken template, and a shell with no tabs is a
    // dead end. Showing all four is recoverable; showing none is not.
    assertThat(perThingSectionsFor(Capabilities())).containsExactly(
      ShellSection.DASHBOARD,
      ShellSection.SQUAWKS,
      ShellSection.TASKS,
      ShellSection.LOGS,
    )
      .inOrder()
  }
}
