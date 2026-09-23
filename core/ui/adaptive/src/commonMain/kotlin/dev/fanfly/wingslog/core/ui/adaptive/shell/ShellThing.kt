package dev.fanfly.wingslog.core.ui.adaptive.shell

import dev.fanfly.wingslog.thing.ThingTemplate

/** Lightweight thing projection used by the shell's switcher. */
data class ShellThing(
  val id: String,
  /**
   * The switcher's primary line — the Thing's own name.
   *
   * Was the tail number, which is a spec key only aviation declares: a home has no tail number and
   * no make or model either, so both lines came out blank and the row rendered as an empty gap with
   * a checkmark. The name is the one label every Thing has.
   */
  val label: String,
  /** The second line: make and model, or the template's identifier. Blank when neither adds anything. */
  val subtitle: String,
  /**
   * This thing's template DNA — the words it is described in, and the features it has.
   *
   * Resolved once here rather than at each call site: every screen below the shell needs it, and
   * resolving per-screen would mean each one holding a [TemplateRegistry] to answer a question the
   * shell has already answered. Null for a caller constructing a [ShellThing] in a test, which then
   * gets the generic lexicon and every capability enabled.
   */
  val template: ThingTemplate? = null,
  /**
   * False when this build cannot interpret [template] and the thing renders degraded (#728).
   *
   * Carried on the switcher projection so the thing still *appears* — never hiding it is the point
   * — while the surfaces that would write to it can tell that they must not.
   */
  val renderable: Boolean = true,
)
