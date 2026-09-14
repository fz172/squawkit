package dev.fanfly.wingslog.core.ui.adaptive

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
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

  private fun appCapability(isDataLogsSupported: Boolean) = AppCapability(
    isDeveloperOptionsSupported = false,
    isCameraCaptureSupported = false,
    isAnonymousLoginSupported = false,
    isAdsSupported = false,
    isDataLogsSupported = isDataLogsSupported,
  )

  private val supported = appCapability(isDataLogsSupported = true)
  private val unsupported = appCapability(isDataLogsSupported = false)

  private val airplaneWithDataLogs = Capabilities(
    sections = listOf(
      Section.SECTION_DASHBOARD,
      Section.SECTION_SQUAWKS,
      Section.SECTION_TASKS,
      Section.SECTION_LOGS,
      Section.SECTION_DATA_LOGS,
    ),
  )

  @Test
  fun theFailOpenDefaultRemovesNothing() {
    // #660: the failure this catches is a capability read with the *wrong default* — before the
    // registry resolves, outside a provider, or from a template that declares nothing — silently
    // removing a section for aviation users. A missing tab is far less noticeable in review than a
    // wrong word, so the default is asserted to be as harmless as the airplane set.
    assertThat(perThingSectionsFor(CurrentThingTemplate.ALL_ENABLED, supported)).containsExactly(
      ShellSection.DASHBOARD,
      ShellSection.SQUAWKS,
      ShellSection.TASKS,
      ShellSection.LOGS,
    ).inOrder()
  }

  @Test
  fun aTemplateThatDeclaresFewerSectionsGetsFewerTabs() {
    val sections = perThingSectionsFor(
      Capabilities(sections = listOf(Section.SECTION_DASHBOARD, Section.SECTION_LOGS)),
      supported,
    )

    assertThat(sections).containsExactly(ShellSection.DASHBOARD, ShellSection.LOGS).inOrder()
    assertThat(sections).doesNotContain(ShellSection.SQUAWKS)
  }

  @Test
  fun theDeclaredOrderIsTheRenderedOrder() {
    // The reason this is a list and not a bool per section: a set of flags cannot express order.
    val sections = perThingSectionsFor(
      Capabilities(sections = listOf(Section.SECTION_LOGS, Section.SECTION_DASHBOARD)),
      supported,
    )

    assertThat(sections).containsExactly(ShellSection.LOGS, ShellSection.DASHBOARD).inOrder()
  }

  @Test
  fun aSectionThisBuildCannotRenderIsDropped() {
    // A template written by a newer client. Rendering the tab would navigate nowhere.
    val sections = perThingSectionsFor(
      Capabilities(sections = listOf(Section.SECTION_DASHBOARD, Section.SECTION_UNKNOWN)),
      supported,
    )

    assertThat(sections).containsExactly(ShellSection.DASHBOARD)
  }

  @Test
  fun declaringNothingFailsOpenRatherThanRemovingAllNavigation() {
    // Fail open: a template with no sections is a broken template, and a shell with no tabs is a
    // dead end. Showing the original four is recoverable; showing none is not.
    assertThat(perThingSectionsFor(Capabilities(), supported)).containsExactly(
      ShellSection.DASHBOARD,
      ShellSection.SQUAWKS,
      ShellSection.TASKS,
      ShellSection.LOGS,
    ).inOrder()
  }

  @Test
  fun aDeclaredDataLogSectionRendersWhenTheBuildSupportsIt() {
    assertThat(perThingSectionsFor(airplaneWithDataLogs, supported)).containsExactly(
      ShellSection.DASHBOARD,
      ShellSection.SQUAWKS,
      ShellSection.TASKS,
      ShellSection.LOGS,
      ShellSection.DATA_LOGS,
    ).inOrder()
  }

  @Test
  fun theRolloutSwitchDropsTheDataLogSectionButNothingElse() {
    // PRD R43: the template declares the section ahead of every host shipping it. Off, the section
    // is absent — never disabled — and the other four are untouched.
    assertThat(perThingSectionsFor(airplaneWithDataLogs, unsupported)).containsExactly(
      ShellSection.DASHBOARD,
      ShellSection.SQUAWKS,
      ShellSection.TASKS,
      ShellSection.LOGS,
    ).inOrder()
  }
}
