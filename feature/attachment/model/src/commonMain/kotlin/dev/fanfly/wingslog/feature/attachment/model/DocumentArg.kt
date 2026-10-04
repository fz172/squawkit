package dev.fanfly.wingslog.feature.attachment.model

import dev.fanfly.wingslog.thing.Attachment
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * An [Attachment] as a navigation argument: *Find tasks in this document* hands the suggestions
 * screen a file already on a record (task population PRD R4), to read without uploading it again.
 * The proto's bytes in URL-safe base64 without padding, like a task draft, so it travels in a
 * route's query string untouched.
 */
fun Attachment.toDocumentArg(): String = encode().toByteString()
  .base64Url()
  .trimEnd('=')

/** The attachment [arg] encodes, or null when it does not decode. */
fun attachmentFromDocumentArg(arg: String): Attachment? =
  arg.decodeBase64()
    ?.let { bytes -> runCatching { Attachment.ADAPTER.decode(bytes) }.getOrNull() }

/** A file the suggestion run's document reader takes: a PDF, or a photo of pages (PRD R7). */
fun Attachment.isReadableDocument(): Boolean =
  type.isFile && (mime_type == "application/pdf" || mime_type.startsWith("image/"))
