package dev.fanfly.wingslog.core.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntityCodecRegistryTest {

  private val registry = EntityCodecRegistry()

  @Test
  fun register_then_codecFor_returns_registered_codec() {
    val codec = bytesCodec()
    registry.register(CollectionKind.Aircraft, codec)

    val retrieved: EntityCodec<ByteArray> = registry.codecFor(CollectionKind.Aircraft)

    assertThat(retrieved).isSameInstanceAs(codec)
  }

  @Test
  fun codecFor_unregistered_kind_throws() {
    // Nothing registered for Technician.
    var threw = false
    try {
      registry.codecFor<ByteArray>(CollectionKind.Technician)
    } catch (e: IllegalStateException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  @Test
  fun registeredKinds_reflects_all_inserted_kinds() {
    assertThat(registry.registeredKinds).isEmpty()

    registry.register(CollectionKind.Aircraft, bytesCodec())
    registry.register(CollectionKind.MaintenanceLog, bytesCodec())

    assertThat(registry.registeredKinds).containsExactly(
      CollectionKind.Aircraft,
      CollectionKind.MaintenanceLog,
    )
  }

  @Test
  fun registeredKinds_does_not_include_unregistered_kind() {
    registry.register(CollectionKind.Aircraft, bytesCodec())

    assertThat(registry.registeredKinds).doesNotContain(CollectionKind.Technician)
  }

  private fun bytesCodec(): EntityCodec<ByteArray> = object : EntityCodec<ByteArray> {
    override fun encode(value: ByteArray): ByteArray = value
    override fun decode(bytes: ByteArray): ByteArray = bytes
  }
}
