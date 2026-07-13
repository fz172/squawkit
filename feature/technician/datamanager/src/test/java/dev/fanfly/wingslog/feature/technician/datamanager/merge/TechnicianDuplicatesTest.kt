package dev.fanfly.wingslog.feature.technician.datamanager.merge

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import org.junit.Test

private const val MEMBER_UID = "uid-member"
private const val OTHER_UID = "uid-other"

class TechnicianDuplicatesTest {

  private fun manual(
    id: String,
    name: String,
    cert: String = "",
    type: CertificateType = CertificateType.CERTIFICATE_TYPE_NONE,
  ) = Technician(id = id, name = name, cert_number = cert, certificate_type = type)

  private fun mirror(uid: String, name: String, cert: String = "") =
    Technician(id = uid, name = name, cert_number = cert, source_uid = uid)

  // ---- manual ↔ self ----

  @Test
  fun aHandTypedCopyOfYourself_isCaughtEvenWithNoCertificates() {
    // You are "XYZ" and you also typed a technician "XYZ" in by hand before the app bootstrapped
    // your profile. Neither has a certificate — the commonest shape of this duplicate.
    val me = manual("self", "XYZ")
    val handTyped = manual("m1", "XYZ")

    val group = findDuplicates(manual = listOf(handTyped), mirrors = emptyList(), self = me)
      .single()

    assertThat(group.resolution).isEqualTo(DuplicateResolution.MERGE_MANUAL)
    // Your real profile survives; the hand-typed copy is the one that goes.
    assertThat(group.keep.id).isEqualTo("self")
    assertThat(group.duplicates.map { it.id }).containsExactly("m1")
    // Name-only, so it still needs confirming.
    assertThat(group.autoSafe).isFalse()
  }

  @Test
  fun aHandTypedCopyOfYourself_matchingByCertNumber_isAutoSafe() {
    val me = manual("self", "XYZ", cert = "AP-123")
    val handTyped = manual("m1", "X. Y. Zed", cert = "AP-123")

    val group = findDuplicates(manual = listOf(handTyped), mirrors = emptyList(), self = me)
      .single()

    assertThat(group.keep.id).isEqualTo("self")
    assertThat(group.autoSafe).isTrue()
  }

  @Test
  fun theSelfRecord_isNeverAmongTheDuplicates() {
    // Whatever else matches, your own profile must never be the row that gets tombstoned.
    val me = manual("self", "XYZ")
    val handTyped = manual("m1", "XYZ")
    val member = mirror(MEMBER_UID, "XYZ")

    val groups = findDuplicates(manual = listOf(handTyped), mirrors = listOf(member), self = me)

    assertThat(groups.flatMap { it.duplicates }.map { it.id }).doesNotContain("self")
  }

  @Test
  fun aRowMatchingBothYouAndAMember_collapsesIntoYou() {
    // You are the authority on your own name — it should not be aliased away to a member who
    // happens to share it.
    val me = manual("self", "XYZ")
    val handTyped = manual("m1", "XYZ")
    val member = mirror(MEMBER_UID, "XYZ")

    val groups = findDuplicates(manual = listOf(handTyped), mirrors = listOf(member), self = me)

    assertThat(groups).hasSize(1)
    assertThat(groups.single().resolution).isEqualTo(DuplicateResolution.MERGE_MANUAL)
    assertThat(groups.single().keep.id).isEqualTo("self")
  }

  @Test
  fun aDifferentlyNamedManualRow_isNotMergedIntoYou() {
    val me = manual("self", "XYZ")
    val other = manual("m1", "Someone Else")

    assertThat(findDuplicates(manual = listOf(other), mirrors = emptyList(), self = me)).isEmpty()
  }

  // ---- manual ↔ mirror: alias, never delete ----

  @Test
  fun manualMatchingMemberByCertNumber_aliasesToTheMember_andIsAutoSafe() {
    val hand = manual("m1", "Bob Squarepants", cert = "AP-123")
    val member = mirror(MEMBER_UID, "Sponge Bob", cert = "AP-123")

    val group = findDuplicates(listOf(hand), listOf(member)).single()

    assertThat(group.resolution).isEqualTo(DuplicateResolution.ALIAS_TO_MEMBER)
    // The mirror is the source of truth — it is what survives.
    assertThat(group.keep.source_uid).isEqualTo(MEMBER_UID)
    assertThat(group.duplicates.map { it.id }).containsExactly("m1")
    assertThat(group.autoSafe).isTrue()
  }

  @Test
  fun manualMatchingMemberByNameOnly_isProposedButNotAutoSafe() {
    val hand = manual("m1", "Sponge Bob")
    val member = mirror(MEMBER_UID, "sponge  bob")

    val group = findDuplicates(listOf(hand), listOf(member)).single()

    assertThat(group.resolution).isEqualTo(DuplicateResolution.ALIAS_TO_MEMBER)
    // Names collide; a human has to confirm.
    assertThat(group.autoSafe).isFalse()
  }

  // ---- case and whitespace insensitivity ----

  @Test
  fun namesMatchRegardlessOfCaseOrSpacing() {
    val me = manual("self", "XYZ Smith")
    val handTyped = manual("m1", "  xyz   smith ")

    val group = findDuplicates(manual = listOf(handTyped), mirrors = emptyList(), self = me)
      .single()

    assertThat(group.keep.id).isEqualTo("self")
  }

  @Test
  fun certificateNumbersMatchRegardlessOfCaseOrSurroundingSpace() {
    val a = manual("m1", "Alice", cert = "ap-123 ")
    val b = manual("m2", "Alicia", cert = " AP-123")

    val group = findDuplicates(listOf(a, b), emptyList()).single()

    assertThat(group.resolution).isEqualTo(DuplicateResolution.MERGE_MANUAL)
    assertThat(group.autoSafe).isTrue()
  }

  @Test
  fun certificateTypesMatchRegardlessOfCaseOnLegacyRows() {
    // Pre-enum rows carry the type as a free string; it must not fail to match on case alone.
    val a = Technician(id = "m1", name = "Bob", cert_type = "certificate_type_amt")
    val b = Technician(id = "m2", name = "Bob", cert_type = "CERTIFICATE_TYPE_AMT")

    assertThat(findDuplicates(listOf(a, b), emptyList())).hasSize(1)
  }

  @Test
  fun mirrorMatchesAHandTypedRowRegardlessOfCase() {
    val hand = manual("m1", "sponge bob", cert = "ap-123")
    val member = mirror(MEMBER_UID, "SPONGE BOB", cert = "AP-123")

    val group = findDuplicates(listOf(hand), listOf(member)).single()

    assertThat(group.resolution).isEqualTo(DuplicateResolution.ALIAS_TO_MEMBER)
    assertThat(group.autoSafe).isTrue()
  }

  // ---- the blank-certificate trap ----

  @Test
  fun uncertificatedTechnicians_areNotCollapsedByTheirBlankCertNumbers() {
    // The FAR 43 owner-mechanic case: no certificate at all. Matching two blanks would fuse every
    // uncertificated technician into one person.
    val a = manual("m1", "Alice Owner")
    val b = manual("m2", "Bob Owner")
    val c = manual("m3", "Carol Owner")

    val groups = findDuplicates(listOf(a, b, c), emptyList())

    assertThat(groups).isEmpty()
  }

  // ---- manual ↔ manual ----

  @Test
  fun twoManualRowsWithSameCertNumber_mergeAndKeepTheRicherRow() {
    val sparse = manual("m1", "Bob")
      .copy(cert_number = "AP-123")
    val rich = manual("m2", "Bob Squarepants", cert = "AP-123")

    val group = findDuplicates(listOf(sparse, rich), emptyList()).single()

    assertThat(group.resolution).isEqualTo(DuplicateResolution.MERGE_MANUAL)
    assertThat(group.keep.id).isEqualTo("m2")
    assertThat(group.duplicates.map { it.id }).containsExactly("m1")
    assertThat(group.autoSafe).isTrue()
  }

  @Test
  fun twoManualRowsMatchingOnNameOnly_areNeverAutoSafe() {
    val a = manual("m1", "John Smith")
    val b = manual("m2", "john smith")

    val group = findDuplicates(listOf(a, b), emptyList()).single()

    assertThat(group.resolution).isEqualTo(DuplicateResolution.MERGE_MANUAL)
    assertThat(group.autoSafe).isFalse()
  }

  @Test
  fun sameNameButDifferentCertNumbers_areTwoDifferentPeople() {
    val a = manual("m1", "John Smith", cert = "AP-111")
    val b = manual("m2", "John Smith", cert = "AP-222")

    assertThat(findDuplicates(listOf(a, b), emptyList())).isEmpty()
  }

  // ---- mirror ↔ mirror: never merged ----

  @Test
  fun twoMirrorsSharingACertNumber_areWarnedAboutButNeverMerged() {
    val one = mirror(MEMBER_UID, "Sponge Bob", cert = "AP-123")
    val two = mirror(OTHER_UID, "Patrick Star", cert = "AP-123")

    val group = findDuplicates(emptyList(), listOf(one, two)).single()

    // Two members are two accounts. Equal cert numbers mean someone mistyped, not that they merge.
    assertThat(group.resolution).isEqualTo(DuplicateResolution.WARN_MIRROR_CONFLICT)
    assertThat(group.autoSafe).isFalse()
  }

  @Test
  fun distinctMirrors_produceNoGroups() {
    val one = mirror(MEMBER_UID, "Sponge Bob", cert = "AP-123")
    val two = mirror(OTHER_UID, "Patrick Star", cert = "AP-456")

    assertThat(findDuplicates(emptyList(), listOf(one, two))).isEmpty()
  }

  // ---- precedence ----

  @Test
  fun aManualRowMatchingBothAMemberAndAnotherManualRow_aliasesToTheMember() {
    // The mirror is the source of truth, so the member claims the row before the manual↔manual
    // pass can merge it away.
    val hand1 = manual("m1", "Sponge Bob", cert = "AP-123")
    val hand2 = manual("m2", "Sponge Bob", cert = "AP-123")
    val member = mirror(MEMBER_UID, "Sponge Bob", cert = "AP-123")

    val groups = findDuplicates(listOf(hand1, hand2), listOf(member))

    assertThat(groups).hasSize(1)
    assertThat(groups.single().resolution).isEqualTo(DuplicateResolution.ALIAS_TO_MEMBER)
    assertThat(groups.single().duplicates.map { it.id }).containsExactly("m1", "m2")
  }

  @Test
  fun noDuplicates_producesNoGroups() {
    val a = manual("m1", "Alice", cert = "AP-111")
    val b = manual("m2", "Bob", cert = "AP-222")
    val member = mirror(MEMBER_UID, "Carol", cert = "AP-333")

    assertThat(findDuplicates(listOf(a, b), listOf(member))).isEmpty()
  }
}
