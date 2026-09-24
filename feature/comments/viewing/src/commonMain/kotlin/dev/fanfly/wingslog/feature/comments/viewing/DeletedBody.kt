package dev.fanfly.wingslog.feature.comments.viewing

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import dev.fanfly.wingslog.core.datetime.toDisplayDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comment_deleted

/**
 * What a deleted comment says instead of its text. Struck through and muted, so it reads as a gap
 * in the conversation rather than as something to go and read — but it stays in place, because the
 * record that a comment was made and withdrawn is the point. See comment.proto.
 */
@Composable
internal fun DeletedBody(deletedAt: Instant?) {
  Text(
    text = stringResource(
      Res.string.comment_deleted,
      deletedAt?.toDisplayDateTime()
        .orEmpty(),
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textDecoration = TextDecoration.LineThrough,
    fontStyle = FontStyle.Italic,
  )
}
