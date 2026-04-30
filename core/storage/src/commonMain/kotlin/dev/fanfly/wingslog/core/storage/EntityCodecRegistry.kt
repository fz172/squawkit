package dev.fanfly.wingslog.core.storage

/**
 * Map of [CollectionKind] to its [EntityCodec]. Constructed at app start via Koin; the registry is
 * the single chokepoint that makes "store a row for an unregistered domain" impossible.
 *
 * Use [verifyCoverage] in production startup or in a unit test to fail fast when a new
 * [CollectionKind] subtype is added without a matching codec.
 */
class EntityCodecRegistry {
  private val codecs = mutableMapOf<CollectionKind, EntityCodec<*>>()

  fun <T : Any> register(kind: CollectionKind, codec: EntityCodec<T>) {
    codecs[kind] = codec
  }

  /** @throws IllegalStateException if no codec is registered for [kind]. */
  @Suppress("UNCHECKED_CAST")
  fun <T : Any> codecFor(kind: CollectionKind): EntityCodec<T> =
    (codecs[kind] as? EntityCodec<T>)
      ?: error("No EntityCodec registered for $kind")

  /** Set of registered [CollectionKind] subtypes — used by coverage tests. */
  val registeredKinds: Set<CollectionKind> get() = codecs.keys.toSet()

  /**
   * Asserts that every [CollectionKind] subtype in [CollectionKind.ALL] has a registered codec.
   * Call from app start (or a unit test) so a forgotten registration fails loudly rather than
   * surfacing later as a "no codec for X" runtime error during a write.
   */
  fun verifyCoverage() {
    val missing = CollectionKind.ALL - codecs.keys
    check(missing.isEmpty()) { "No EntityCodec registered for: $missing" }
  }
}
