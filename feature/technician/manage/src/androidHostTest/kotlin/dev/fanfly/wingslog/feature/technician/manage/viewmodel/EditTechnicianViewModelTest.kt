package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.model.technician.FAA_AMT
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.CertificateType
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val TECH_ID = "tech-1"

/**
 * The add/edit flow's half of #684: what the form offers, what a pre-#684 record loads as, and what
 * a save writes back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditTechnicianViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val registry = BakedInTemplateRegistry(appVersionCode = 1)

  private lateinit var technicianManager: TechnicianManager
  private lateinit var sharingManager: SharingManager
  private lateinit var fleetManager: FleetManager

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    technicianManager = mockk(relaxed = true)
    sharingManager = mockk(relaxed = true)
    fleetManager = mockk(relaxed = true)

    every { technicianManager.observeSelfId() } returns flowOf(null)
    fleetOf(registry.canonicalById("airplane")!!)
    coEvery { technicianManager.updateTechnician(any()) } returns Result.success(true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fleetOf(vararg templates: ThingTemplate) {
    every { fleetManager.observeFleetDashboard() } returns flowOf(
      templates.map {
        FleetEntry(
          thing = Thing(id = it.id, template = it),
          shared = false,
          role = ShareRole.SHARE_ROLE_OWNER,
        )
      }
    )
  }

  private fun viewModel(id: String? = null) = EditTechnicianViewModel(
    technicianManager = technicianManager,
    sharingManager = sharingManager,
    fleetManager = fleetManager,
    templateRegistry = registry,
    savedStateHandle = SavedStateHandle(mapOf(Screen.TECHNICIAN_ID to (id ?: "new"))),
  )

  @Test
  fun theFormOffersWhatTheAccountsTemplatesDeclare() = runTest(testDispatcher) {
    fleetOf(CanonicalTemplates.HOME)

    assertThat(viewModel().uiState.value.offered.map { it.key })
      .containsExactly("electrician", "plumber", "hvac_epa608", "general_contractor")
  }

  @Test
  fun aMixedAccountOffersEveryDomainItHolds() = runTest(testDispatcher) {
    fleetOf(CanonicalTemplates.HOME, registry.canonicalById("airplane")!!)

    assertThat(viewModel().uiState.value.offered.map { it.key })
      .containsAtLeast(FAA_AMT, "electrician")
  }

  @Test
  fun anAccountWithNoCredentialedTemplateOffersNothing() = runTest(testDispatcher) {
    // The form draws no certification section at all in this case — an empty section header is a
    // promise of a control that is not coming.
    fleetOf(CanonicalTemplates.BIKE)

    assertThat(viewModel().uiState.value.offered).isEmpty()
  }

  @Test
  fun aPre684RecordLoadsAsOneCertification() = runTest(testDispatcher) {
    // Nothing migrates fields 3-7; the read derives. Loading straight from `certifications` would
    // show the mechanic who signs the annual as uncertified, and then SAVE that.
    every { technicianManager.loadTechnician(TECH_ID) } returns flowOf(
      Technician(
        id = TECH_ID,
        name = "Avery",
        certificate_type = CertificateType.CERTIFICATE_TYPE_AMT,
        cert_number = "AMT-4471",
        cert_expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
      )
    )

    val state = viewModel(TECH_ID).uiState.value

    assertThat(state.certifications).hasSize(1)
    assertThat(state.certifications.single().type).isEqualTo(FAA_AMT)
    assertThat(state.certifications.single().number).isEqualTo("AMT-4471")
  }

  @Test
  fun savingClearsTheLegacyFieldsItJustReadFrom() = runTest(testDispatcher) {
    every { technicianManager.loadTechnician(TECH_ID) } returns flowOf(
      Technician(
        id = TECH_ID,
        name = "Avery",
        certificate_type = CertificateType.CERTIFICATE_TYPE_AMT,
        cert_number = "AMT-4471",
        source_uid = "uid-1",
      )
    )
    val written = slot<Technician>()
    coEvery { technicianManager.updateTechnician(capture(written)) } returns Result.success(true)

    viewModel(TECH_ID).save()

    // One answer, not two: the legacy fields have already been folded into the list above, and
    // leaving them behind would be a second source nothing keeps in step.
    assertThat(written.captured.certifications.map { it.type }).containsExactly(FAA_AMT)
    assertThat(written.captured.certificate_type)
      .isEqualTo(CertificateType.CERTIFICATE_TYPE_NONE)
    assertThat(written.captured.cert_number).isEmpty()
    // Provenance is not the form's to drop: it drives the linked badge and the self-signed check.
    assertThat(written.captured.source_uid).isEqualTo("uid-1")
  }

  @Test
  fun addingAndRemovingCertificationsEditsTheList() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.updateName("Avery")
    vm.addCertification(FAA_AMT)
    vm.updateCertificationNumber(0, "AMT-1")
    // The same credential twice is not a thing anyone holds; the add menu hides it, and the state
    // refuses it even if something else asks.
    vm.addCertification(FAA_AMT)

    assertThat(vm.uiState.value.certifications).hasSize(1)

    vm.removeCertification(0)
    assertThat(vm.uiState.value.certifications).isEmpty()
  }

  @Test
  fun aNeverExpiringCertificationIsWrittenWithNoDate() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.updateName("Avery")
    vm.addCertification(FAA_AMT)
    vm.updateCertificationExpireLimit(0, CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES)
    val written = slot<Technician>()
    coEvery { technicianManager.updateTechnician(capture(written)) } returns Result.success(true)

    vm.save()

    assertThat(written.captured.certifications).containsExactly(
      Certification(
        type = FAA_AMT,
        expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES,
      )
    )
  }
}
