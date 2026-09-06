package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test

private const val THING_ID = "thing-1"

class TaskStatusManagerImplTest {

  private val scope = EntityScope.thingChildUnsafe("uid-1", THING_ID)
  private val resolver: ThingScopeResolver = mockk()
  private val taskStore: EntityStore<MaintenanceTask> = mockk()
  private val logStore: EntityStore<MaintenanceLog> = mockk()
  private val storeFactory: EntityStoreFactory = mockk()
  private val dueManager: TaskDueManager = mockk()

  private val soon = MaintenanceTask(id = "soon", title = "Due soon")
  private val later = MaintenanceTask(id = "later", title = "Due later")
  private val done = MaintenanceTask(id = "done", title = "One-time, complied", is_one_time = true)

  @Before
  fun setUp() {
    every { storeFactory.create<MaintenanceTask>(CollectionKind.MaintenanceTask) } returns taskStore
    every { storeFactory.create<MaintenanceLog>(CollectionKind.MaintenanceLog) } returns logStore
    every { resolver.resolve(THING_ID) } returns flowOf(scope)
    every { taskStore.observeAll(scope) } returns flowOf(listOf(done, later, soon).map { StorageEntity(it.id, it, Instant.DISTANT_PAST) })
    every { logStore.observeAll(scope) } returns flowOf(emptyList())
    every { dueManager.computeNextDue(soon, any(), any()) } returns DueMetadata(nextDueDate = LocalDate(2026, 10, 1))
    every { dueManager.computeNextDue(later, any(), any()) } returns DueMetadata(nextDueDate = LocalDate(2027, 3, 1))
    every { dueManager.computeNextDue(done, any(), any()) } returns DueMetadata(status = DueStatus.COMPLIED)
  }

  private fun manager() = TaskStatusManagerImpl(
    resolver, storeFactory, dueManager,
    clock = object : kotlin.time.Clock { override fun now() = Instant.parse("2026-09-06T12:00:00Z") },
    timeZone = TimeZone.UTC,
  )

  @Test
  fun activeFirstByUrgency_thenComplied() = runTest {
    val result = manager().observeTasksWithStatus(THING_ID).first()
    assertThat(result.map { it.card.id }).containsExactly("soon", "later", "done").inOrder()
    assertThat(result.last().dueStatus.status).isEqualTo(DueStatus.COMPLIED)
  }

  @Test
  fun noScope_emitsEmpty() = runTest {
    every { resolver.resolve(THING_ID) } returns flowOf(null)
    assertThat(manager().observeTasksWithStatus(THING_ID).first()).isEmpty()
  }

  @Test
  fun logsReachTheDueManager() = runTest {
    val log = MaintenanceLog(id = "l1", timestamp = toWireInstant(0L), inspection_ids = listOf("soon"))
    every { logStore.observeAll(scope) } returns flowOf(listOf(StorageEntity(log.id, log, Instant.DISTANT_PAST)))
    every { dueManager.computeNextDue(soon, listOf(log), any()) } returns DueMetadata(nextDueDate = LocalDate(2027, 12, 1))
    val result = manager().observeTasksWithStatus(THING_ID).first()
    assertThat(result.map { it.card.id }).containsExactly("later", "soon", "done").inOrder()
  }
}
