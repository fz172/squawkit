package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CUSTOM_CERTIFICATION_PREFIX
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.offeredCertifications
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.CertificationEntry
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.CertificateType
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.Technician
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class EditTechnicianUiState(
  val id: String = "",
  val name: String = "",
  val certifications: List<CertificationEntry> = emptyList(),
  /** What the account's templates declare — empty means the form offers no certifications at all. */
  val offered: List<OfferedCertification> = emptyList(),
  val isSelf: Boolean = false,
  val isLoading: Boolean = false,
  val isSaving: Boolean = false,
  val saveSuccess: Boolean = false,
  val deleteSuccess: Boolean = false,
  val error: String? = null,
)

class EditTechnicianViewModel(
  private val technicianManager: TechnicianManager,
  private val sharingManager: SharingManager,
  private val fleetManager: FleetManager,
  private val templateRegistry: TemplateRegistry,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val technicianId: String? =
    savedStateHandle.get<String>(Screen.TECHNICIAN_ID)
      ?.takeIf { it != "new" }

  /**
   * The record as it was loaded, so a save edits it rather than rebuilding it.
   *
   * `updateTechnician` writes the whole proto, so anything the form does not carry — `source_uid`,
   * and any field a newer build wrote that this one does not know about — is lost by constructing a
   * fresh `Technician` from the UI state. Provenance drives the linked badge and the self-signed
   * check (design §7.3/§7.5), so losing it is not cosmetic.
   */
  private var loaded: Technician = Technician()

  private val _uiState =
    MutableStateFlow(EditTechnicianUiState(isLoading = technicianId != null))
  val uiState = _uiState.asStateFlow()

  init {
    observeOfferedCertifications()
    if (technicianId != null) {
      loadTechnician(technicianId)
      observeSelfId(technicianId)
    }
  }

  /**
   * What the account can record, from the templates its Things carry (PRD §8.6).
   *
   * The whole fleet, not the selected Thing: the roster is account-scoped, which is exactly why the
   * retired `technician_certificates` capability could not answer this.
   */
  private fun observeOfferedCertifications() {
    viewModelScope.launch {
      fleetManager.observeFleetDashboard()
        .collect { entries ->
          val offered =
            templateRegistry.offeredCertifications(entries.map { it.thing })
          _uiState.update { it.copy(offered = offered) }
        }
    }
  }

  private fun observeSelfId(id: String) {
    viewModelScope.launch {
      technicianManager.observeSelfId()
        .collect { selfId ->
          _uiState.update { it.copy(isSelf = selfId == id) }
        }
    }
  }

  private fun loadTechnician(id: String) {
    viewModelScope.launch {
      val technician = technicianManager.loadTechnician(id)
        .firstOrNull()
      if (technician == null) {
        _uiState.update {
          it.copy(
            isLoading = false,
            error = "Failed to load technician"
          )
        }
        return@launch
      }
      loaded = technician
      _uiState.update {
        it.copy(
          id = technician.id,
          name = technician.name,
          // resolvedCertifications, not the field: a record written before #684 carries its one
          // certificate in fields 3-7, and reading the list directly would show it as uncertified.
          certifications = technician.resolvedCertifications()
            .map { certification ->
              CertificationEntry(
                type = certification.type,
                number = certification.number,
                label = certification.label,
                expireLimit = certification.expire_limit,
                expiration = certification.expiration?.let { ts ->
                  Instant.fromEpochSeconds(ts.getEpochSecond(), ts.getNano())
                },
              )
            },
          isLoading = false,
        )
      }
    }
  }

  fun updateName(name: String) {
    _uiState.update { it.copy(name = name) }
  }

  fun addCertification(type: String) {
    _uiState.update { state ->
      if (state.certifications.any { it.type == type }) return@update state
      state.copy(certifications = state.certifications + CertificationEntry(type = type))
    }
  }

  /**
   * A credential no template declares, named by the user.
   *
   * The key is `custom_N` over the entries already present, so two customs on one record stay
   * distinct and a renamed one keeps the number and expiry recorded under it — the same reason
   * `Spec.label` sits beside the key rather than inside it (#781).
   */
  fun addCustomCertification() {
    _uiState.update { state ->
      val taken = state.certifications.filter { it.isCustom }
        .mapNotNull {
          it.type.removePrefix(CUSTOM_CERTIFICATION_PREFIX)
            .toIntOrNull()
        }
      val next = generateSequence(1) { it + 1 }.first { it !in taken }
      state.copy(
        certifications = state.certifications +
          CertificationEntry(type = "$CUSTOM_CERTIFICATION_PREFIX$next"),
      )
    }
  }

  fun removeCertification(index: Int) {
    _uiState.update { state ->
      state.copy(
        certifications = state.certifications.filterIndexed { i, _ -> i != index },
      )
    }
  }

  /**
   * Upper-cased as typed, the way an `is_identifier` spec field is. A certificate number is matched
   * exactly — duplicate detection decides two rows are one person by comparing them — so the casing
   * has to be settled on the way in rather than at each comparison.
   */
  fun updateCertificationNumber(index: Int, number: String) {
    updateCertification(index) { it.copy(number = number.uppercase()) }
  }

  /** Title-cased as typed — a credential is a name, so it reads like one. */
  fun updateCertificationLabel(index: Int, label: String) {
    updateCertification(index) { it.copy(label = label.titleCase()) }
  }

  fun updateCertificationExpireLimit(index: Int, expireLimit: CertExpireLimit) {
    updateCertification(index) { it.copy(expireLimit = expireLimit) }
  }

  fun updateCertificationExpiration(index: Int, expiration: Instant) {
    updateCertification(index) { it.copy(expiration = expiration) }
  }

  private fun updateCertification(
    index: Int,
    edit: (CertificationEntry) -> CertificationEntry
  ) {
    _uiState.update { state ->
      state.copy(
        certifications = state.certifications.mapIndexed { i, entry ->
          if (i == index) edit(entry) else entry
        },
      )
    }
  }

  fun delete() {
    val id = _uiState.value.id
    if (id.isBlank()) return
    viewModelScope.launch {
      val result = technicianManager.deleteTechnician(id)
      if (result.isSuccess) {
        _uiState.update { it.copy(deleteSuccess = true) }
      } else {
        _uiState.update {
          it.copy(
            error = result.exceptionOrNull()?.message
              ?: "Failed to delete technician"
          )
        }
      }
    }
  }

  fun save() {
    val currentState = _uiState.value
    if (currentState.name.isBlank()) {
      _uiState.update { it.copy(error = "Name is required") }
      return
    }

    _uiState.update { it.copy(isSaving = true, error = null) }

    viewModelScope.launch {
      val technician = loaded.copy(
        id = currentState.id,
        name = currentState.name,
        certifications = currentState.certifications.map { it.toCertification() },
        // The legacy single certificate is a read path only: whatever it said has already been
        // folded into `certifications` above, and leaving it behind would be a second answer to
        // the same question that nothing keeps in step.
        cert_type = "",
        cert_number = "",
        cert_expiration = null,
        cert_expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_UNKNOWN,
        certificate_type = CertificateType.CERTIFICATE_TYPE_NONE,
      )

      val result = technicianManager.updateTechnician(technician)
      if (result.isSuccess) {
        // Editing the self-record changes how this member appears on a signed log everywhere they
        // are a member, so republish the mirror to each share (§7.2). Best-effort: it queues in the
        // outbox on failure, and must not block the save from reporting success.
        if (currentState.isSelf) sharingManager.publishTechnicianMirror()
        _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
      } else {
        _uiState.update {
          it.copy(
            isSaving = false,
            error = result.exceptionOrNull()?.message
              ?: "Failed to save technician"
          )
        }
      }
    }
  }

  /**
   * Every word's first letter, the rest left as typed — so "IBM" and "McIntosh" survive.
   *
   * The same rule the Thing form applies to a `title_case` spec field (#781), not
   * `LexiconFormatter.titleCase`: that one is for words the app or a template author wrote, and its
   * minor-word handling rewrites what the user typed as they type it.
   */
  private fun String.titleCase(): String =
    split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

  private fun CertificationEntry.toCertification() = Certification(
    type = type,
    number = number,
    label = label,
    expiration = if (expireLimit == CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES) {
      null
    } else {
      expiration?.let { toWireInstant(it.epochSeconds, it.nanosecondsOfSecond) }
    },
    expire_limit = expireLimit,
  )
}
