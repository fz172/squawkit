package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection

/** A jump from one record to an associated one: the [section] that lists it, and its id. */
data class RecordJump(val section: ShellSection, val recordId: String)
