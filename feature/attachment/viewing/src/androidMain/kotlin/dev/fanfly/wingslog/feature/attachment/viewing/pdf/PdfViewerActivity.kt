package dev.fanfly.wingslog.feature.attachment.viewing.pdf

import androidx.core.os.BundleCompat
import android.view.View
import android.net.Uri
import android.content.Intent
import android.content.ActivityNotFoundException
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
    // The fragment sets the document itself once its view exists, as the library's samples load one.
    val viewer = CitedPagePdfViewerFragment().apply {
      arguments = bundleOf(
        CitedPagePdfViewerFragment.ARG_DOCUMENT to document,
        CitedPagePdfViewerFragment.ARG_PAGE to intent.getIntExtra(EXTRA_PAGE, 0),
      )
    }
    supportFragmentManager.commitNow { replace(CONTAINER_ID, viewer) }
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

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // Once: after a rotation the library restores its own document and place.
    if (documentUri == null) {
      documentUri = BundleCompat.getParcelable(requireArguments(), ARG_DOCUMENT, Uri::class.java)
    }
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
    scrollWhenReady((page - 1).coerceAtMost(document.pageCount - 1), attemptsLeft = SCROLL_ATTEMPTS)
  }

  /**
   * The library reports the document loaded before its view has it, and the view throws when asked
   * to scroll without one; so this waits a frame at a time until it does. Never crashes the viewer:
   * if the view never takes the document, it stays at the first page.
   */
  private fun scrollWhenReady(pageIndex: Int, attemptsLeft: Int) {
    val view = view ?: return
    view.post {
      if (!isAdded) return@post
      if (pdfView.pdfDocument == null) {
        if (attemptsLeft > 0) scrollWhenReady(pageIndex, attemptsLeft - 1)
        return@post
      }
      try {
        pdfView.scrollToPage(pageIndex)
      } catch (_: IllegalStateException) {
        // Not ready after all: the first page is a fine place to be.
      }
    }
  }

  /**
   * The library could not open it (a device it cannot run on, a damaged file): the device's own
   * viewers get the file instead, as before this screen existed.
   */
  override fun onLoadDocumentError(error: Throwable) {
    super.onLoadDocumentError(error)
    val activity = activity ?: return
    val document = documentUri ?: return
    val outside = Intent(Intent.ACTION_VIEW)
      .setDataAndType(document, "application/pdf")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
      activity.startActivity(Intent.createChooser(outside, null))
    } catch (_: ActivityNotFoundException) {
      return
    }
    activity.finish()
  }

  internal companion object {
    const val ARG_PAGE = "page"
    const val ARG_DOCUMENT = "document"

    /** About a second of frames: the view takes the document within one or two. */
    private const val SCROLL_ATTEMPTS = 60
    private const val STATE_JUMPED = "jumped"
  }
}
