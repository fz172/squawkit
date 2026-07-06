package dev.fanfly.wingslog.core.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollectionKindCoverageTest {

  private val allKnownKinds: List<CollectionKind> = listOf(
    CollectionKind.Aircraft,
    CollectionKind.MaintenanceTask,
    CollectionKind.MaintenanceLog,
    CollectionKind.MaintenanceOverview,
    CollectionKind.Technician,
    CollectionKind.UserInfo,
    CollectionKind.FeatureLab,
    CollectionKind.Squawk,
  )

  @Test
  fun every_known_kind_is_in_ALL_and_sizes_match() {
    for (kind in allKnownKinds) {
      assertThat(CollectionKind.ALL).contains(kind)
    }
    assertThat(CollectionKind.ALL).hasSize(allKnownKinds.size)
  }

  @Test
  fun every_kind_has_unique_wireName() {
    val wireNames = CollectionKind.ALL.map { it.wireName }
    assertThat(wireNames.toSet()).hasSize(CollectionKind.ALL.size)
  }

  @Test
  fun fromWire_round_trips_for_every_kind() {
    for (kind in CollectionKind.ALL) {
      assertThat(CollectionKind.fromWire(kind.wireName)).isSameInstanceAs(kind)
    }
  }

  @Test
  fun fromWire_unknown_wire_name_throws() {
    var threw = false
    try {
      CollectionKind.fromWire("totally_unknown_collection")
    } catch (e: IllegalStateException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  @Test
  fun verifyCoverage_passes_when_all_kinds_registered() {
    val registry = EntityCodecRegistry()
    for (kind in CollectionKind.ALL) {
      registry.register(kind, noOpCodec())
    }
    // Must not throw.
    registry.verifyCoverage()
  }

  @Test
  fun verifyCoverage_throws_when_one_kind_missing() {
    val registry = EntityCodecRegistry()
    // Register all except UserInfo.
    for (kind in CollectionKind.ALL.filter { it != CollectionKind.UserInfo }) {
      registry.register(kind, noOpCodec())
    }

    var threw = false
    var message = ""
    try {
      registry.verifyCoverage()
    } catch (e: IllegalStateException) {
      threw = true
      message = e.message ?: ""
    }

    assertThat(threw).isTrue()
    assertThat(message).contains(CollectionKind.UserInfo.wireName)
  }

  /** A minimal no-op codec used only to satisfy the registry — no serialization happens. */
  private fun noOpCodec(): EntityCodec<ByteArray> =
    object : EntityCodec<ByteArray> {
      override fun encode(value: ByteArray): ByteArray = value
      override fun decode(bytes: ByteArray): ByteArray = bytes
    }
}
