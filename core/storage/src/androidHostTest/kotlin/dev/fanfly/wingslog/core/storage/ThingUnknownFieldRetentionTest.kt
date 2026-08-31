package dev.fanfly.wingslog.core.storage

import com.google.common.truth.Truth.assertThat
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import okio.Buffer
import okio.ByteString
import org.junit.Test

/**
 * Task A11 of the Aircraft → Thing migration (docs/product/thing_migration_design.md §6, PRD §9.2).
 *
 * The migration's whole safety argument rests on one property of the wire format: **a client built
 * against an older version of the schema round-trips fields it has never heard of.** During the
 * migration window a device on the pre-migration build can read, edit, and write back a Thing whose
 * fields 7–11 the server has already backfilled (§5.1 step 2a) — and if that write dropped them, the
 * backfill would silently un-happen for every account with a stale device, with nothing to notice it.
 *
 * **This now also covers the retired fields 2–6.** They were reserved in #668, so the current
 * `Thing` cannot even name them — which makes it exactly the older-schema reader this test is
 * about, meeting bytes it has no definition for. A migrated document still carries them, and
 * they have to survive a read-edit-write cycle or the reservation would quietly destroy data it
 * promised only to stop using.
 *
 * Note what is and isn't simulated here. There is no longer a generated class for the pre-migration
 * schema (fields 1–6) to decode with — the rename replaced it. So the test exercises the same
 * mechanism one version further along: the CURRENT `Thing` (fields 1–11) reads bytes carrying a field
 * 12 it does not know, edits a field it does, and writes back. That is structurally the identical
 * case — an adapter meeting a higher-numbered field it has no definition for — and it runs against
 * the real generated adapter rather than a hand-rolled stand-in.
 */
class ThingUnknownFieldRetentionTest {

  private val thing = Thing(
    id = "ac-1",
    // Fields 2-6 are reserved (#668) — a Thing cannot set them any more, which is itself
    // part of what this test now demonstrates: the bytes for them survive in migrated
    // documents as unknown fields, exactly as fields 7 and 8 did before them.
    name = "N12345",
    // `value_`, not `value`: Wire escapes the proto field name in Kotlin codegen. The wire format
    // and the TypeScript bindings are unaffected — only the Kotlin identifier changes.
    spec = listOf(Spec(key = "make", value_ = "Cessna")),
    components = listOf(
      Component(
        id = "ac-1:airframe:0",
        slot_key = "airframe",
        label = "Airframe"
      )
    ),
  )

  /** Bytes as a *newer* client would write them: a real Thing plus a field this build cannot name. */
  private fun withFutureField(value: String): ByteArray {
    val extra = Buffer()
    val writer = ProtoWriter(extra)
    ProtoAdapter.STRING.encodeWithTag(writer, FUTURE_TAG, value as String?)
    // A proto message's encoding is just its fields concatenated, so appending is exactly what a
    // newer client emitting one more field would produce.
    val out = Buffer()
    out.write(Thing.ADAPTER.encode(thing))
    out.writeAll(extra)
    return out.readByteArray()
  }

  @Test
  fun anUnknownFieldSurvivesADecodeEditReEncodeRoundTrip() {
    val fromFutureClient = withFutureField("set-by-a-newer-build")

    val decoded = Thing.ADAPTER.decode(fromFutureClient)
    // The field this build has no definition for is retained rather than discarded...
    assertThat(decoded.unknownFields).isNotEqualTo(ByteString.EMPTY)
    // ...and the fields it does know decode normally.
    assertThat(decoded.name).isEqualTo("N12345")

    // The edit a stale device would make: change one known field, leave everything else alone.
    val edited = decoded.copy(name = "N54321")
    val rewritten = Thing.ADAPTER.encode(edited)

    val reread = Thing.ADAPTER.decode(rewritten)
    assertThat(reread.name).isEqualTo("N54321")
    assertThat(reread.unknownFields).isEqualTo(decoded.unknownFields)
  }

  @Test
  fun theBackfilledFieldsSurviveAStaleClientsEditByTheSameMechanism() {
    // The concrete case the migration depends on, stated in the fields' own terms: whatever the
    // server backfilled must read back identically after a round-trip through a client's edit.
    // (template_id/template_version are gone — a Thing without DNA is legacy, and legacy is
    // always airplane, so nothing needs storing to say so.)
    val edited = Thing.ADAPTER.decode(Thing.ADAPTER.encode(thing))
      .copy(name = "N54321")
    val reread = Thing.ADAPTER.decode(Thing.ADAPTER.encode(edited))

    assertThat(reread.name).isEqualTo("N54321")
    assertThat(reread.spec).isEqualTo(thing.spec)
    assertThat(reread.components).isEqualTo(thing.components)
  }

  @Test
  fun aPreMigrationPayloadDecodesWithTheNewFieldsEmpty() {
    // The other direction: data written before the backfill ran. Fields 7–11 are simply absent, and
    // must decode to their defaults rather than failing — this is what every not-yet-migrated
    // document looks like to a Phase 1 client.
    val legacyShaped = Thing(
      id = "ac-1",
    )

    val decoded = Thing.ADAPTER.decode(Thing.ADAPTER.encode(legacyShaped))

    assertThat(decoded).isEqualTo(legacyShaped)
    assertThat(decoded.name).isEmpty()
    assertThat(decoded.spec).isEmpty()
    assertThat(decoded.components).isEmpty()
  }

  private companion object {
    /**
     * One past the highest field this build knows (`template = 12`).
     *
     * This must be bumped whenever `Thing` gains a field, and the bump is not cosmetic: if this
     * names a tag the schema actually defines, the test stops exercising unknown-field retention
     * and starts exercising a type mismatch — encoding a `string` where the schema expects a
     * message. It would still pass or fail for reasons that have nothing to do with what it
     * claims to prove.
     */
    const val FUTURE_TAG = 13
  }
}
