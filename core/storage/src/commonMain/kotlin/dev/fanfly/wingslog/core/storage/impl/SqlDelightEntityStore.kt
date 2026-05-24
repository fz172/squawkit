package dev.fanfly.wingslog.core.storage.impl

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityCodec
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLDelight-backed [EntityStore]. One instance per [CollectionKind]; created via
 * [dev.fanfly.wingslog.core.storage.EntityStoreFactory].
 *
 * @param ioContext context for SQLDelight Flow extensions. Pass `Dispatchers.IO` in production;
 *   tests provide `UnconfinedTestDispatcher` so emissions are immediate.
 * @param clock injected so tests can advance virtual time deterministically.
 */
@OptIn(ExperimentalTime::class)
class SqlDelightEntityStore<T : Any>(
  override val kind: CollectionKind,
  private val codec: EntityCodec<T>,
  private val db: WingsLogDatabase,
  private val ioContext: CoroutineContext,
  private val clock: Clock = Clock.System,
) : EntityStore<T> {

  override fun observeAll(scope: EntityScope): Flow<List<StorageEntity<T>>> =
    db.schemaQueries.selectAll(
      kind,
      scope.toPath()
    )
      .asFlow()
      .mapToList(ioContext)
      .map { rows -> rows.map { it.toEntity() } }

  override fun observe(
    id: String,
    scope: EntityScope,
  ): Flow<StorageEntity<T>?> =
    db.schemaQueries.selectOne(
      kind,
      scope.toPath(),
      id
    )
      .asFlow()
      .mapToOneOrNull(ioContext)
      .map { it?.toEntity() }

  override suspend fun put(
    id: String,
    value: T,
    scope: EntityScope,
  ) {
    val now = clock.now().toEpochMilliseconds()
    db.schemaQueries.upsert(
      collection = kind,
      scope_path = scope.toPath(),
      id = id,
      payload = codec.encode(value),
      payload_schema = kind.schemaName,
      updated_at = now,
      remote_updated_at = null,
      dirty = true,
      deleted = false,
    )
  }

  override suspend fun delete(
    id: String,
    scope: EntityScope,
  ) {
    val existing = db.schemaQueries.selectOne(
      kind,
      scope.toPath(),
      id
    ).awaitAsOneOrNull()
    val payloadBytes = existing?.payload ?: ByteArray(0)
    val now = clock.now().toEpochMilliseconds()
    db.schemaQueries.upsert(
      collection = kind,
      scope_path = scope.toPath(),
      id = id,
      payload = payloadBytes,
      payload_schema = kind.schemaName,
      updated_at = now,
      remote_updated_at = null,
      dirty = true,
      deleted = true,
    )
  }

  // SQLDelight `selectAll` / `selectOne` return generated row types with `id`, `payload`,
  // `updated_at` columns. Map them through the codec.
  private fun SelectAllRow.toEntity(): StorageEntity<T> = StorageEntity(
    id = id,
    value = codec.decode(payload),
    updatedAt = Instant.fromEpochMilliseconds(updated_at),
  )

  private fun SelectOneRow.toEntity(): StorageEntity<T> = StorageEntity(
    id = id,
    value = codec.decode(payload),
    updatedAt = Instant.fromEpochMilliseconds(updated_at),
  )
}

// SQLDelight names generated row classes after the query — typealiases keep this file readable.
private typealias SelectAllRow = dev.fanfly.wingslog.core.storage.db.SelectAll
private typealias SelectOneRow = dev.fanfly.wingslog.core.storage.db.SelectOne
