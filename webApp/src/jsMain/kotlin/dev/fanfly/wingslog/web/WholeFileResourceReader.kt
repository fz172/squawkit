@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)

package dev.fanfly.wingslog.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceReader

/**
 * Works around a Firebase Hosting + Compose resources incompatibility on the web.
 *
 * Compose reads packed string resources (`*.cvr`) with HTTP **Range** requests by byte offset.
 * Firebase Hosting gzip-compresses those responses (and ignores `no-transform` / a non-text
 * `Content-Type`), so a `Range` request is evaluated against the *compressed* bytes: offsets past
 * the compressed length return `416`, and smaller offsets return the wrong bytes — both decode to
 * garbage and crash string loading ("Invalid symbol '_' …").
 *
 * Whole-file fetches, by contrast, decompress transparently. This reader therefore delegates
 * everything to the platform default reader except [readPart], which fetches the *entire* file once
 * (cached) and slices locally — eliminating Range requests. Web-only; native platforms are
 * unaffected and keep the default reader.
 */
@OptIn(InternalResourceApi::class)
private class WholeFileResourceReader(private val delegate: ResourceReader) :
  ResourceReader {
  private val mutex = Mutex()
  private val cache = mutableMapOf<String, ByteArray>()

  override suspend fun read(path: String): ByteArray = delegate.read(path)

  override suspend fun readPart(
    path: String,
    offset: Long,
    size: Long
  ): ByteArray {
    val whole = mutex.withLock {
      cache.getOrPut(path) { delegate.read(path) }
    }
    val start = offset.toInt()
    return whole.copyOfRange(start, start + size.toInt())
  }

  override fun getUri(path: String): String = delegate.getUri(path)
}

/**
 * Provides the [WholeFileResourceReader] for the wrapped [content] so every Compose string lookup on
 * web avoids Range requests. Wrap the whole app once near the root.
 */
@OptIn(InternalResourceApi::class)
@Composable
internal fun ProvideWholeFileResourceReader(content: @Composable () -> Unit) {
  val default = LocalResourceReader.current
  val reader = remember(default) { WholeFileResourceReader(default) }
  CompositionLocalProvider(
    LocalResourceReader provides reader,
    content = content
  )
}
