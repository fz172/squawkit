package dev.fanfly.wingslog.feature.attachment.model

import dev.fanfly.wingslog.thing.Attachment
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * An [Attachment] as a navigation argument: the Add Tasks sheet hands the suggestions screen the
 * manuals picked there, already stored, so the run reads them without picking again. The proto's
 * bytes in URL-safe base64 without padding, like a task draft, so it travels in a route's query
 * string untouched.
 */
fun Attachment.toDocumentArg(): String = encode().toByteString()
  .base64Url()
  .trimEnd('=')

/** The attachment [arg] encodes, or null when it does not decode. */
fun attachmentFromDocumentArg(arg: String): Attachment? =
  arg.decodeBase64()
    ?.let { bytes -> runCatching { Attachment.ADAPTER.decode(bytes) }.getOrNull() }

/**
 * Several attachments as one argument: each [toDocumentArg], comma-separated (base64url has no
 * comma). The Add Tasks sheet hands the suggestions screen the files picked there this way.
 */
fun List<Attachment>.toDocumentsArg(): String = joinToString(",") { it.toDocumentArg() }

/** The attachments [arg] encodes; one that does not decode is left out. */
fun attachmentsFromDocumentsArg(arg: String): List<Attachment> =
  arg.split(",").filter { it.isNotEmpty() }.mapNotNull(::attachmentFromDocumentArg)
