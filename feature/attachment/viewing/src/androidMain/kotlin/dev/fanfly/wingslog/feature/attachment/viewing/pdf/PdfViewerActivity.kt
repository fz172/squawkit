package dev.fanfly.wingslog.feature.attachment.viewing.pdf

import android.os.Bundle
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.pdf.PdfDocument
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.viewer.fragment.PdfViewerFragment

/**
 * A PDF opened inside the app, at a page where one is given: the page an AI suggestion cited
 * (task population PRD R30). Android has no standard way to ask another app's viewer for a page,
 * so the app shows the file itself with Jetpack PDF.
 *
 * Started by `AttachmentOpenerAndroid` by class name ([CLASS_NAME]) with the file's content URI as
 * the intent's data and [EXTRA_PAGE], 1-based; the opener falls back to the outside viewer when this
 * screen cannot be started.
 */
class PdfViewerActivity : FragmentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val document = intent.data ?: run {
      finish()
      return
    }
    val container = FrameLayout(this).apply { id = CONTAINER_ID }
    // Edge to edge from targetSdk 35: keep the viewer's toolbar and search clear of the bars.
    ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
      view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
      WindowInsetsCompat.CONSUMED
    }
    setContentView(container)
    if (savedInstanceState != null) return
    val viewer = CitedPagePdfViewerFragment().apply {
      arguments = bundleOf(CitedPagePdfViewerFragment.ARG_PAGE to intent.getIntExtra(EXTRA_PAGE, 0))
    }
    supportFragmentManager.commitNow { replace(CONTAINER_ID, viewer) }
    viewer.documentUri = document
  }

  companion object {
    /** For starting it by name, from a module that cannot see this class. */
    const val CLASS_NAME = "dev.fanfly.wingslog.feature.attachment.viewing.pdf.PdfViewerActivity"

    /** The 1-based page to open at; 0 or absent opens at the start. */
    const val EXTRA_PAGE = "dev.fanfly.wingslog.extra.PDF_PAGE"

    private const val CONTAINER_ID = 0x5d0c
  }
}

/**
 * The library's viewer, scrolled once to the requested page when the document has loaded. Its
 * `pdfView` is experimental in 1.0.0-beta01; a library update that moves it is caught here at
 * compile time.
 */
@OptIn(ExperimentalPdfApi::class)
class CitedPagePdfViewerFragment : PdfViewerFragment() {

  /** Only the first load jumps: a rotation or reload keeps where the reader has scrolled to. */
  private var jumped = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    jumped = savedInstanceState?.getBoolean(STATE_JUMPED) ?: false
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_JUMPED, jumped)
  }

  override fun onLoadDocumentSuccess(document: PdfDocument) {
    super.onLoadDocumentSuccess(document)
    val page = requireArguments().getInt(ARG_PAGE, 0)
    if (jumped || page <= 0) return
    jumped = true
    // The view counts from 0; a citation from 1. A page past the end goes to the last.
    pdfView.scrollToPage((page - 1).coerceAtMost(document.pageCount - 1))
  }

  internal companion object {
    const val ARG_PAGE = "page"
    private const val STATE_JUMPED = "jumped"
  }
}
