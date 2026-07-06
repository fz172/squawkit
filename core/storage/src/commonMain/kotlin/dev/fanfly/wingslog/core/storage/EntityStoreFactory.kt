package dev.fanfly.wingslog.core.storage

import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.core.storage.impl.SqlDelightEntityStore
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Creates [EntityStore] instances on demand. Avoids per-domain Koin singletons; each manager
 * receives the factory and asks for the store it needs.
 *
 * Example: `factory.create<Aircraft>(CollectionKind.Aircraft)`.
 */
@OptIn(ExperimentalTime::class)
class EntityStoreFactory(
  private val db: WingsLogDatabase,
  private val codecs: EntityCodecRegistry,
  private val ioContext: CoroutineContext,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
  private val clock: Clock = Clock.System,
) {
  fun <T : Any> create(kind: CollectionKind): EntityStore<T> =
    SqlDelightEntityStore(
      kind = kind,
      codec = codecs.codecFor(kind),
      db = db,
      ioContext = ioContext,
      writeLock = writeLock,
      clock = clock,
    )
}
