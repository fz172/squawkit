package dev.fanfly.wingslog.feature.comments.datamanager

import dev.fanfly.wingslog.feature.comments.model.CommentTarget

/** Words typed for a record and not yet posted, kept while its sheet is closed. */
internal data class CommentDraft(val target: CommentTarget, val text: String)
