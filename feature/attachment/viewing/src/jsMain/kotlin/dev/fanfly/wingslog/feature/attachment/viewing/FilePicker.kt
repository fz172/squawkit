package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLInputElement

@Composable
actual fun rememberFilePicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit,
): () -> Unit {
  val scope = rememberCoroutineScope()
  return remember(onResult, onReadError, scope) {
    {
      val input = (document.createElement("input") as HTMLInputElement).apply {
        type = "file"
        multiple = true
      }
      input.onchange = {
        val handles = fileHandles(input.asDynamic().files)
        scope.launch {
          val picked = readBrowserFiles(handles)
          if (picked.anyFailed) onReadError()
          if (picked.files.isNotEmpty()) onResult(picked.files)
        }
        null
      }
      input.click()
    }
  }
}
