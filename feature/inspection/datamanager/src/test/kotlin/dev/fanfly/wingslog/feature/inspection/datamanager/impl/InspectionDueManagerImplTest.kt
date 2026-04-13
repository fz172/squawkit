package dev.fanfly.wingslog.feature.inspection.datamanager.impl

import com.google.common.truth.Truth
import io.mockk.mockk
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test

class InspectionDueManagerImplTest {

  private lateinit var clock: Clock
  private lateinit var inspectionDueManagerImpl: InspectionDueManagerImpl

  @Before
  fun setUp() {
    clock = mockk(relaxed = true)
    inspectionDueManagerImpl = InspectionDueManagerImpl(clock, TimeZone.Companion.UTC)
  }

  @Test
  fun dummyTest() {
    // Add real unit tests later
    Truth.assertThat(true).isTrue()
  }
}