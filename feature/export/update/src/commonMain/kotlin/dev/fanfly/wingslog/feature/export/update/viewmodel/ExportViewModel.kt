package dev.fanfly.wingslog.feature.export.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.ExportCompleted
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.displayLabel
import dev.fanfly.wingslog.core.template.displaySubtitle
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryEmailSource
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryInfo
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryOutcome
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportJobCoordinator
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.export.datamanager.ExportRunPolicy
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType.ATTACHMENT_TYPE_LINK
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Coordinates export selection state and progress for the export destination.
 */
class ExportViewModel(
  private val exportManager: ExportManager,
  private val jobCoordinator: ExportJobCoordinator,
  private val fleetManager: FleetManager,
  private val logsManager: MaintenanceLogManager,
  private val taskDataManager: TaskDataManager,
  private val squawkManager: SquawkManager,
  private val subscriptionManager: SubscriptionManager,
  private val auth: FirebaseAuth,
  private val currentThingTemplate: CurrentThingTemplate,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  private val runPolicy: ExportRunPolicy,
  clock: Clock = Clock.System,
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

  private val today = clock.now()
    .toLocalDateTime(timeZone).date
  private val defaultConfiguring = ExportUiState.Configuring(
    customStart = today.minus(DatePeriod(months = 12)),
    customEnd = today,
  )

  private val _state = MutableStateFlow<ExportUiState>(defaultConfiguring)
  val state: StateFlow<ExportUiState> = _state.asStateFlow()

  // One-shot outcome of an explicit "Send to my email" tap, for the UI to surface as a snackbar.
  private val _deliveryEvents = Channel<ExportDeliveryOutcome>()
  val deliveryEvents = _deliveryEvents.receiveAsFlow()

  private var lastConfiguring: ExportUiState.Configuring = defaultConfiguring
  private var hasInitializedSelection = false
  private var latestDeliveryInfo: ExportDeliveryInfo? = null
  // The job whose completion has already been counted, so re-observing it never double-logs.
  private var loggedJobId: String? = null

  init {
    observeThing()
    observeDeliveryInfo()
    observeJob()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeThing() {
    viewModelScope.launch {
      fleetManager.observeFleetDashboard()
        .flatMapLatest { entries ->
          // Export operates on the user's own logbook only; shared things are read-through
          // pointers into another account's tree and aren't exported here.
          val things = entries.filter { !it.shared }
            .map { it.thing }
          if (things.isEmpty()) {
            flowOf(emptyList())
          } else {
            combine(
              things.map { item ->
                combine(
                  logsManager.observeLogs(item.id),
                  taskDataManager.observeTasks(item.id),
                  squawkManager.observeSquawks(item.id),
                ) { logs, tasks, squawks ->
                  val attachmentSizeBytes =
                    logs.sumOf { it.attachments.exportedBytes() } +
                      tasks.sumOf { it.attachments.exportedBytes() } +
                      squawks.sumOf { it.attachments.exportedBytes() }
                  item.toSelectionRow(
                    logCount = logs.size,
                    attachmentSizeBytes = attachmentSizeBytes,
                  )
                }
              }
            ) { rows -> rows.toList() }
          }
        }
        .collect { rows ->
          val currentConfig =
            _state.value as? ExportUiState.Configuring ?: lastConfiguring
          val rowIds = rows.map { it.thingId }
            .toSet()
          val selectedIds = if (!hasInitializedSelection) {
            hasInitializedSelection = true
            rowIds
          } else {
            currentConfig.selectedThingIds.intersect(rowIds)
          }
          lastConfiguring = currentConfig.copy(
            things = rows,
            selectedThingIds = selectedIds,
            isLoadingThings = false,
          )
            .recomputeEstimates()
          // Only the setup screen shows the rows. A running, finished, or interrupted export keeps
          // its own state; the refreshed setup waits in lastConfiguring for when it comes back.
          when (val current = _state.value) {
            is ExportUiState.Configuring -> _state.value = lastConfiguring
            // A result screen composed before the rows arrived (process death, cold notification
            // tap) fills in the names now.
            is ExportUiState.Success -> jobCoordinator.job.value?.let { job ->
              _state.value = current.copy(
                selectedTailNumbers = lastConfiguring.labelsFor(job.request.thingIds)
              )
            }

            else -> Unit
          }
        }
    }
  }

  private fun observeDeliveryInfo() {
    viewModelScope.launch {
      combine(
        auth.authStateChanged,
        subscriptionManager.canEmailExports(),
      ) { user, canEmail -> user to canEmail }
        .collect { (user, emailDeliveryEnabled) ->
          val signedIn = user != null && !user.isAnonymous
          val authEmail = user?.email.orEmpty()
            .trim()
          // A signed-in user with an email is who email delivery is for; guests and email-less
          // accounts are local-only regardless of the gate.
          val eligibleForEmail = signedIn && authEmail.isNotBlank()
          val info = if (emailDeliveryEnabled && eligibleForEmail) {
            ExportDeliveryInfo(
              authEmail,
              ExportDeliveryEmailSource.AUTH_FALLBACK
            )
          } else {
            null
          }
          // When the Pro gate is off but the user would otherwise get email delivery, surface the
          // option shown-locked (a promo) instead of hiding it. Local export is unaffected.
          val locked = eligibleForEmail && !emailDeliveryEnabled
          latestDeliveryInfo = info
          val current =
            _state.value as? ExportUiState.Configuring ?: return@collect
          val next = current.copy(
            resolvedDeliveryInfo = info,
            emailDeliveryLocked = locked,
          )
          lastConfiguring = next
          _state.value = next
        }
    }
  }

  fun onToggleThing(id: String) = reduceConfiguring { current ->
    val selected = if (id in current.selectedThingIds) {
      current.selectedThingIds - id
    } else {
      current.selectedThingIds + id
    }
    current.copy(selectedThingIds = selected)
      .recomputeEstimates()
  }

  fun onSelectAll() = reduceConfiguring { current ->
    current.copy(
      selectedThingIds = current.things.map { it.thingId }
        .toSet()
    )
      .recomputeEstimates()
  }

  fun onClearAll() = reduceConfiguring { current ->
    current.copy(selectedThingIds = emptySet())
      .recomputeEstimates()
  }

  /**
   * Toggles a report format, keeping at least one selected so the export always produces a document.
   */
  fun onToggleFormat(format: ExportFormat) = reduceConfiguring { current ->
    val next = if (format in current.formats) {
      if (current.formats.size == 1) return@reduceConfiguring current
      current.formats - format
    } else {
      current.formats + format
    }
    current.copy(formats = next)
      .recomputeEstimates()
  }

  fun onDateRangeChange(option: DateRangeOption) =
    reduceConfiguring { current ->
      current.copy(dateRange = option)
        .recomputeEstimates()
    }

  /**
   * Updates the inclusive custom range in one state transition.
   */
  fun onCustomRangeChange(start: LocalDate, end: LocalDate) =
    reduceConfiguring { current ->
      val normalizedStart = if (start <= end) start else end
      val normalizedEnd = if (start <= end) end else start
      current.copy(
        dateRange = DateRangeOption.Custom,
        customStart = normalizedStart,
        customEnd = normalizedEnd,
      )
        .recomputeEstimates()
    }

  /**
   * Mirrors the coordinator's job into screen state. The job outlives this ViewModel where the
   * platform allows it (#343), so a screen opened mid-export or after completion picks it up here.
   */
  private fun observeJob() {
    viewModelScope.launch {
      jobCoordinator.job.collect { job ->
        if (job == null) {
          // Cancelled or cleared. Setup-side states (Configuring, Interrupted) are not the job's
          // to change; only a stale job-derived screen falls back to setup.
          if (_state.value.isJobDerived()) _state.value = lastConfiguring
          return@collect
        }
        val progress = job.progress
        // On the terminal success only: a cancelled or failed export produced no archive, and
        // §13 counts exports that finished.
        if (progress is ExportProgress.Success && loggedJobId != job.id) {
          loggedJobId = job.id
          analytics.log(
            ExportCompleted(
              templateId = currentThingTemplate.templateId,
              format = job.request.formats.joinToString("+") { it.name.lowercase() },
              thingCount = job.request.thingIds.size,
            )
          )
        }
        val current = _state.value
        // The result screen already on show may carry a "send to email" in flight; keep it.
        if (current is ExportUiState.Success && progress is ExportProgress.Success &&
          current.exportId == progress.exportId
        ) return@collect
        _state.value = progress.toUiState(job.request)
      }
    }
  }

  /**
   * Starts export generation using the current configuration.
   */
  fun onExport() {
    val configuring = _state.value as? ExportUiState.Configuring ?: return
    if (configuring.selectedThingIds.isEmpty()) return
    lastConfiguring = configuring
    // Shown at once rather than waiting for the coordinator's first emission.
    _state.value = ExportUiState.Running(ExportProgressStep.COLLECTING_DATA, 0)
    jobCoordinator.start(configuring.toRequest())
  }

  /**
   * The host reports the app left the foreground. On platforms whose [ExportRunPolicy] stops the
   * work, an in-flight export is abandoned and the screen explains that it has to be restarted.
   * Any other state, or a platform that keeps running, is untouched.
   */
  fun onAppBackgrounded() {
    if (!runPolicy.stopWhenBackgrounded) return
    if (_state.value !is ExportUiState.Running) return
    _state.value = ExportUiState.Interrupted
    jobCoordinator.cancel()
  }

  /**
   * Re-runs the export that was interrupted, with the configuration it was started from.
   */
  fun onRestart() {
    if (_state.value !is ExportUiState.Interrupted) return
    _state.value = lastConfiguring
    onExport()
  }

  /**
   * Cancels an in-flight export and restores the last editable configuration.
   */
  fun onCancel() {
    _state.value = lastConfiguring
    jobCoordinator.cancel()
  }

  /**
   * Dismisses terminal export state without discarding the previous configuration.
   */
  fun onDone() {
    _state.value = lastConfiguring
    jobCoordinator.clear()
  }

  /**
   * Explicitly requests delivery of the just-finished export to the user's email. Delivery is
   * never triggered automatically — this is the only path that sends one.
   */
  fun onSendToEmail() {
    val success = _state.value as? ExportUiState.Success ?: return
    if (success.isSendingEmail) return
    _state.value = success.copy(isSendingEmail = true)
    viewModelScope.launch {
      val outcome = exportManager.resendDelivery(success.exportId)
      val current = _state.value as? ExportUiState.Success
      if (current != null && current.exportId == success.exportId) {
        _state.value = current.copy(
          isSendingEmail = false,
          persistedDeliveryState = when (outcome) {
            is ExportDeliveryOutcome.Sent -> "SENT"
            is ExportDeliveryOutcome.Failed -> "FAILED"
            else -> current.persistedDeliveryState
          },
          deliveryFailureMessage = if (outcome is ExportDeliveryOutcome.Failed) {
            outcome.reason
          } else {
            current.deliveryFailureMessage
          },
        )
      }
      _deliveryEvents.send(outcome)
    }
  }

  /**
   * Returns from an error or interrupted state to the last editable configuration.
   */
  fun onRetry() {
    _state.value = lastConfiguring
    jobCoordinator.clear()
  }

  override fun onCleared() {
    // Screen-bound platforms (iOS, web) end the export with the screen; Android's worker carries on.
    if (!runPolicy.survivesLeavingScreen) jobCoordinator.cancel()
    super.onCleared()
  }

  /**
   * Resolves the archive bytes for [exportId] for platforms whose Download action has no durable
   * local file handle (web) and must fetch the bytes on demand.
   */
  suspend fun fetchArchiveBytes(exportId: String): ByteArray? =
    exportManager.downloadArchiveBytes(exportId)

  private fun reduceConfiguring(
    transform: (ExportUiState.Configuring) -> ExportUiState.Configuring,
  ) {
    val current = _state.value as? ExportUiState.Configuring ?: return
    val next = transform(current)
    lastConfiguring = next
    _state.value = next
  }

  private fun ExportUiState.Configuring.toRequest() = ExportRequest(
    thingIds = selectedThingIds.toList(),
    dateRange = when (dateRange) {
      DateRangeOption.AllTime -> ExportDateRange.AllTime
      DateRangeOption.Last12Months -> ExportDateRange.LastNMonths(12)
      DateRangeOption.Custom -> ExportDateRange.Custom(customStart, customEnd)
    },
    includeOpenSquawks = true,
    formats = formats,
    destinationEmail = latestDeliveryInfo?.destinationEmail,
    destinationEmailSource = latestDeliveryInfo?.source?.name,
  )

  private fun ExportUiState.isJobDerived(): Boolean =
    this is ExportUiState.Running || this is ExportUiState.Success || this is ExportUiState.Error

  private fun ExportUiState.Configuring.labelsFor(thingIds: List<String>): List<String> =
    things.filter { it.thingId in thingIds }
      .map { it.label }

  // Summary fields come from the job's own request, not the current setup: after process death or
  // a notification tap, the setup is still at its defaults.
  private fun ExportProgress.toUiState(request: ExportRequest): ExportUiState = when (this) {
    is ExportProgress.Running -> ExportUiState.Running(step, percent)
    is ExportProgress.Success -> ExportUiState.Success(
      exportId = exportId,
      fileName = fileName,
      displayLocation = displayLocation,
      displayLocationKind = displayLocationKind,
      filePath = filePath,
      sizeBytes = sizeBytes,
      formats = request.formats,
      selectedTailNumbers = lastConfiguring.labelsFor(request.thingIds),
      dateRange = when (request.dateRange) {
        ExportDateRange.AllTime -> DateRangeOption.AllTime
        is ExportDateRange.LastNMonths -> DateRangeOption.Last12Months
        is ExportDateRange.Custom -> DateRangeOption.Custom
      },
      customStart = (request.dateRange as? ExportDateRange.Custom)?.start
        ?: lastConfiguring.customStart,
      customEnd = (request.dateRange as? ExportDateRange.Custom)?.endInclusive
        ?: lastConfiguring.customEnd,
      deliveryInfo = latestDeliveryInfo,
      emailDeliveryLocked = lastConfiguring.emailDeliveryLocked,
      persistedDeliveryState = persistedDeliveryState,
      deliveryFailureMessage = deliveryFailureMessage,
    )

    is ExportProgress.Error -> ExportUiState.Error(message)
  }

  private fun ExportUiState.Configuring.recomputeEstimates(): ExportUiState.Configuring {
    val selectedRows = things.filter { it.thingId in selectedThingIds }
    val logCount = selectedRows.sumOf { it.logCount }
    val attachmentBytes = selectedRows.sumOf { it.attachmentSizeBytes }
    // Per-format contribution; attachments and README ride along regardless of selection.
    val reportBytes = formats.sumOf { format ->
      when (format) {
        ExportFormat.CSV -> 64_000L + (logCount * 600L) + (selectedRows.size * 8_000L)
        ExportFormat.PDF -> 28_000L + (logCount * 380L) + (selectedRows.size * 12_000L)
        ExportFormat.XLSX -> 40_000L + (logCount * 220L) + (selectedRows.size * 10_000L)
      }
    }
    return copy(
      estimatedLogCount = logCount,
      estimatedSizeBytes = if (selectedRows.isEmpty()) 0L else reportBytes + attachmentBytes,
    )
  }

  private fun Thing.toSelectionRow(
    logCount: Int,
    attachmentSizeBytes: Long,
  ): ThingSelectionRow {
    val template = templateRegistry.forThingWithFallback(this)
    return ThingSelectionRow(
      thingId = id,
      label = displayLabel(template),
      subtitle = displaySubtitle(template),
      logCount = logCount,
      attachmentSizeBytes = attachmentSizeBytes,
      iconKey = template.icon,
    )
  }

  private fun List<Attachment>.exportedBytes(): Long = filter { attachment ->
    attachment.type != ATTACHMENT_TYPE_LINK
  }.sumOf { attachment ->
    attachment.size_bytes.coerceAtLeast(0L)
  }
}
