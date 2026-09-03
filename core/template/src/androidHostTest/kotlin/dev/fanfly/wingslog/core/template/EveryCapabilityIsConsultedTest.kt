package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * That every capability the proto declares is actually read somewhere (#659).
 *
 * **A capability nobody consults is indistinguishable from one that is always on**, and the airplane
 * template turns everything on — so a flag added to `Capabilities` and then forgotten produces a
 * working app, a green suite, and a promise the template system does not keep. It surfaces only when
 * a second preset turns it off and the UI it was supposed to remove is still there.
 *
 * This does not check that a gate is *correct*; the per-gate tests do that by calling their decision
 * with the capability off. It checks the cheaper and more easily forgotten thing: that the flag is
 * wired at all.
 */
class EveryCapabilityIsConsultedTest {

  /**
   * Capabilities that deliberately have no reader, each with the reason and where it went.
   *
   * An exemption has to be argued, which is the point of listing them here rather than dropping
   * them from the scan.
   */
  private val deferred = mapOf(
    // The certificate fields it guards live on EditTechnicianScreen, a root destination reachable
    // from both settings and the log form. Technicians are account-scoped, so no single thing's
    // template can answer it — the same scope problem as the technician string revert (#682).
    // Superseded by #684, where certifications move onto the person and roles are derived.
    "technician_certificates" to "#684",
  )

  @Test
  fun everyCapabilityFieldIsReadSomewhere() {
    val declared = capabilityFieldNames()
    assertThat(declared).isNotEmpty()

    val sources = repoRoot().walkTopDown()
      .filter { it.extension == "kt" && "/build/" !in it.path }
      .joinToString("\n") { it.readText() }

    val unread = declared.filterNot { field ->
      // Either read through the CompositionLocal, or passed to a decision function as a parameter.
      sources.contains("LocalThingCapabilities.current.$field") ||
        sources.contains("capabilities.$field")
    }

    assertThat(unread - deferred.keys).isEmpty()
  }

  @Test
  fun everyDeferredCapabilityIsStillDeclared() {
    // A stale exemption is worse than none: it silently excuses a field that no longer exists, and
    // would go on excusing a *new* field that happened to reuse the name.
    assertThat(capabilityFieldNames()).containsAtLeastElementsIn(deferred.keys)
  }

  private fun capabilityFieldNames(): List<String> {
    val proto = File(
      repoRoot(),
      "core/model/src/commonMain/proto/thing/capabilities.proto"
    ).readText()
    val body = proto.substringAfter("message Capabilities {")
      .substringBefore("\n}")
    return Regex(
      """^\s*(?:repeated\s+)?\S+\s+([a-z0-9_]+)\s*=\s*\d+;""",
      RegexOption.MULTILINE
    )
      .findAll(body)
      .map { it.groupValues[1] }
      .toList()
  }

  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = requireNotNull(dir.parentFile)
    }
    return dir
  }
}
