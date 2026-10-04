package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.thing.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals

class PageFragmentTest {
  private val pdf = Attachment(id = "b1", mime_type = "application/pdf")

  @Test
  fun aPdfOpensAtTheCitedPage() {
    assertEquals("#page=12", pageFragment(pdf, 12))
  }

  @Test
  fun withoutAPageAskedForTheRecordsOwnPageIsUsed() {
    assertEquals("#page=40", pageFragment(pdf.copy(open_page = 40), null))
    assertEquals("#page=12", pageFragment(pdf.copy(open_page = 40), 12))
  }

  @Test
  fun noPageAPageBeforeTheFirstOrAnotherFileOpensAtTheStart() {
    assertEquals("", pageFragment(pdf, null))
    assertEquals("", pageFragment(pdf, 0))
    assertEquals("", pageFragment(Attachment(id = "b2", mime_type = "image/jpeg"), 3))
  }
}
