package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.model.technician.FAA_AMT
import dev.fanfly.wingslog.core.model.technician.FAA_REPAIRMAN
import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.CertificateType
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.CertificationDef
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test
import kotlin.time.Instant
import com.squareup.wire.Instant as WireInstant

/**
 * Certifications on the person, and the roles derived from them (#684, PRD §8.6).
 *
 * Two claims carry the design and are what these assert:
 *
 * - **The pre-#684 backfill is derivable, not stored.** Nothing migrates fields 3-7; every read
 *   goes through `resolvedCertifications`. If that read is wrong, an A&P silently becomes an
 *   uncertified helper on every screen at once.
 * - **A role is never stored.** It is recomputed from the keys present, so it cannot disagree with
 *   the certifications underneath it — and an uncertified person having no role is the answer, not
 *   a gap.
 */
class TechnicianCertificationsTest {

  private val registry = BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE)

  private fun thingOf(template: ThingTemplate) = Thing(id = template.id, template = template)

  // ---- the transitional read ----

  @Test
  fun aRecordWrittenAfterTheChangeReadsItsOwnList() {
    val technician = Technician(
      name = "Avery",
      certifications = listOf(Certification(type = FAA_AMT, number = "AMT-1")),
      // Present and ignored: a record carrying both is one the new list already answers for.
      certificate_type = CertificateType.CERTIFICATE_TYPE_REPAIRMAN,
      cert_number = "R-9",
    )

    assertThat(technician.resolvedCertifications())
      .containsExactly(Certification(type = FAA_AMT, number = "AMT-1"))
  }

  @Test
  fun theLegacyEnumBecomesTheAviationKey() {
    val expiration = Instant.fromEpochSeconds(1_700_000_000)
      .let { WireInstant.ofEpochSecond(it.epochSeconds, 0L) }
    val technician = Technician(
      certificate_type = CertificateType.CERTIFICATE_TYPE_AMT,
      cert_number = "AMT-4471",
      cert_expiration = expiration,
      cert_expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
    )

    assertThat(technician.resolvedCertifications()).containsExactly(
      Certification(
        type = FAA_AMT,
        number = "AMT-4471",
        expiration = expiration,
        expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
      )
    )
  }

  @Test
  fun theStringFieldTheEnumReplacedStillResolves() {
    // Written before the enum existed, in whatever case the caller happened to use.
    val technician = Technician(cert_type = "certificate_type_repairman", cert_number = "R-1")

    assertThat(technician.resolvedCertifications().map { it.type })
      .containsExactly(FAA_REPAIRMAN)
  }

  @Test
  fun aNumberWithNothingNamingItIsKeptUnderAnEmptyKey() {
    // The old form only offered the number field once a type was picked, so this ought to be
    // unreachable — but dropping a certificate number somebody entered is not an acceptable way to
    // find out we were wrong about that.
    val technician = Technician(cert_number = "AP-123")

    assertThat(technician.resolvedCertifications()).containsExactly(
      Certification(
        type = "",
        number = "AP-123",
        expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES,
      )
    )
  }

  @Test
  fun anUncertifiedPersonResolvesToNothing() {
    assertThat(Technician(name = "the neighbor").resolvedCertifications()).isEmpty()
  }

  // ---- what the account offers ----

  @Test
  fun theOfferedSetComesFromTheAccountsTemplates() {
    val offered = registry.offeredCertifications(listOf(thingOf(CanonicalTemplates.HOME)))

    assertThat(offered.map { it.key }).containsExactly(
      "electrician", "plumber", "hvac_epa608", "general_contractor",
    )
    // An account with no airplane is not offered an A&P — the roster is account-scoped, which is
    // the whole reason the per-Thing capability could not answer this.
    assertThat(offered.map { it.key }).doesNotContain(FAA_AMT)
  }

  @Test
  fun aMixedAccountOffersBothDomains() {
    val offered = registry.offeredCertifications(
      listOf(thingOf(CanonicalTemplates.HOME), thingOf(registry.canonicalById("airplane")!!)),
    )

    assertThat(offered.map { it.key }).containsAtLeast("electrician", FAA_AMT)
  }

  @Test
  fun anAccountWithNoCredentialedTemplateOffersNothing() {
    // `bike` declares none, which is what `technician_certificates: false` used to say — stated
    // where the scope is right rather than as a flag on one Thing.
    assertThat(registry.offeredCertifications(listOf(thingOf(CanonicalTemplates.BIKE)))).isEmpty()
  }

  @Test
  fun anEmptyFleetOffersEverythingRatherThanNothing() {
    // The roster is reachable from settings before anything has been added. No Things means the
    // domain is unknown, not absent — offering nothing would leave someone able to add a mechanic
    // and unable to say what they are certified in.
    assertThat(registry.offeredCertifications(emptyList()))
      .isEqualTo(registry.knownCertifications())
    assertThat(registry.offeredCertifications(emptyList())).isNotEmpty()
  }

  @Test
  fun theOfferedLabelsComeFromThisBuildNotFromFrozenDna() {
    // A Thing froze the preset's bytes at creation, so its DNA carries whatever label shipped then.
    // Same rule as the lexicon: the words are app UI, only the key is data.
    val staleDna = CanonicalTemplates.HOME.copy(
      certifications = listOf(CertificationDef(key = "electrician", label = "Sparky")),
    )

    val offered = registry.offeredCertifications(listOf(thingOf(staleDna)))

    assertThat(offered.first { it.key == "electrician" }.label).isEqualTo("Electrician")
  }

  @Test
  fun aTemplateThisBuildDoesNotCarryFallsBackToItsOwnDeclaration() {
    val unknown = ThingTemplate(
      id = "spaceship",
      display_name = "Spaceship",
      certifications = listOf(CertificationDef(key = "faa_ast", label = "Commercial Astronaut")),
    )

    val offered = registry.offeredCertifications(listOf(thingOf(unknown)))

    assertThat(offered.map { it.label }).containsExactly("Commercial Astronaut")
  }

  // ---- derived roles ----

  @Test
  fun aRoleIsTheDomainTheCredentialImplies() {
    val amt = Technician(certifications = listOf(Certification(type = FAA_AMT)))

    assertThat(amt.derivedRoles(registry.knownCertifications())).containsExactly("Airplane")
  }

  @Test
  fun twoCredentialsAreTwoRolesOnOneRecord() {
    // The A&P who also services the car. One contact, because any model pushing users toward one
    // record per domain would manufacture exactly the duplicates the merge sheet exists to clean up.
    val both = Technician(
      certifications = listOf(
        Certification(type = FAA_AMT),
        Certification(type = "ase"),
      ),
    )

    assertThat(both.derivedRoles(registry.knownCertifications()))
      .containsExactly("Airplane", "Automotive")
      .inOrder()
  }

  @Test
  fun anUncertifiedPersonCarriesNoRole() {
    // Accepted rather than worked around: nothing about the neighbor who clears the gutters says
    // which domain they belong to, and the behavior that falls out — offered on every Thing — is
    // the one you would have picked anyway.
    assertThat(Technician(name = "the neighbor").derivedRoles(registry.knownCertifications()))
      .isEmpty()
  }

  @Test
  fun aCredentialFromAPresetThisBuildLacksIsUntaggedRatherThanAnError() {
    val shared = Technician(certifications = listOf(Certification(type = "faa_ast")))

    assertThat(shared.derivedRoles(registry.knownCertifications())).isEmpty()
  }

  @Test
  fun aRoleIsKnownEvenWhenTheAccountOwnsNoSuchThing() {
    // knownCertifications, not offeredCertifications: a technician linked from someone else's
    // shared boat arrives on a household account, and their credential still has a name.
    val abyc = Technician(certifications = listOf(Certification(type = "abyc")))

    assertThat(abyc.derivedRoles(registry.knownCertifications())).containsExactly("Boat")
    assertThat(abyc.derivedRoles(registry.offeredCertifications(listOf(thingOf(CanonicalTemplates.HOME)))))
      .isEmpty()
  }

  @Test
  fun aLegacyRecordIsTaggedWithoutHavingBeenMigrated() {
    // The claim the whole no-migration argument rests on.
    val legacy = Technician(certificate_type = CertificateType.CERTIFICATE_TYPE_REPAIRMAN)

    assertThat(legacy.derivedRoles(registry.knownCertifications())).containsExactly("Airplane")
  }

  // ---- what the presets declare ----

  @Test
  fun theAviationKeysAreExactlyWhatTheFrozenEnumCouldStore() {
    // The backfill is only lossless while these two exist: `resolvedCertifications` maps REPAIRMAN
    // and AMT onto them, and a preset that renamed one would strip the credential off every record
    // predating #684 without any test noticing.
    val airplane = registry.canonicalById("airplane")!!

    assertThat(airplane.certifications.map { it.key })
      .containsExactly(FAA_REPAIRMAN, FAA_AMT)
  }

  @Test
  fun everyDeclaredCertificationIsUsable() {
    CanonicalTemplates.ALL.forEach { template ->
      template.certifications.forEach { def ->
        assertThat(def.key).isNotEmpty()
        assertThat(def.label).isNotEmpty()
      }
    }
  }

  @Test
  fun noPresetClaimsTheCustomNamespace() {
    // `custom_N` belongs to the add flow, for a credential the user names themselves. A template
    // claiming one would collide with whatever they typed.
    CanonicalTemplates.ALL.forEach { template ->
      assertThat(template.structuralProblems()).isEmpty()
      template.certifications.forEach { def ->
        assertThat(def.key).doesNotContain(CUSTOM_CERTIFICATION_PREFIX)
      }
    }
  }

  @Test
  fun aCustomCredentialCarriesNoRole() {
    // Nothing declares it, so nothing implies a domain — the same answer as an uncertified person,
    // reached the same way.
    val custom = Technician(
      certifications = listOf(
        Certification(type = "custom_1", label = "Certified Welding Inspector"),
      ),
    )

    assertThat(custom.derivedRoles(registry.knownCertifications())).isEmpty()
  }
}
