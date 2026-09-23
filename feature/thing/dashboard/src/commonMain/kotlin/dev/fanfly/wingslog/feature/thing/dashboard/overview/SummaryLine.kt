package dev.fanfly.wingslog.feature.thing.dashboard.overview

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Flattens a stored description to one run of text for the rail rows. A description that ends in a
 * newline — or wraps a blank line — otherwise renders an empty extra line, making the text taller
 * than the badge and date beside it, which the row then centres above them.
 */
internal fun String.asSummaryLine(): String =
  replace(WHITESPACE_RUN, " ").trim()
