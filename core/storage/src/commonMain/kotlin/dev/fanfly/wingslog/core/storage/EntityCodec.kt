package dev.fanfly.wingslog.core.storage

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter

/**
 * Translates a domain value to and from the on-disk byte representation.
 *
 * For protobuf-backed types (the only kind in R1), [WireCodec] is sufficient. The codec is the
 * type witness for an [EntityStore]: a store can only be constructed if a matching codec is
 * registered with [EntityCodecRegistry], which makes "wrote a row with no codec for it" impossible.
 */
interface EntityCodec<T : Any> {
  fun encode(value: T): ByteArray
  fun decode(bytes: ByteArray): T
}

/**
 * [EntityCodec] backed by a Wire [ProtoAdapter]. Proto wire format is rename-tolerant by design,
 * which is what gives the storage layer its forward-compatibility guarantee
 * (see storage_r1_design.md §4.2.2).
 */
class WireCodec<T : Message<T, *>>(private val adapter: ProtoAdapter<T>) : EntityCodec<T> {
  override fun encode(value: T): ByteArray = adapter.encode(value)
  override fun decode(bytes: ByteArray): T = adapter.decode(bytes)
}
