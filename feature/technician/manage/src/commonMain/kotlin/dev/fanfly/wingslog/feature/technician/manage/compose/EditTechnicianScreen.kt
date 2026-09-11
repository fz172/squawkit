package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.DestructiveActionCard
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.common.compose.GroupedCard
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.EditTechnicianViewModel
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.CertificationInputFields
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.save
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.certifications
import wingslog.feature.technician.sharedassets.generated.resources.delete_technician
import wingslog.feature.technician.sharedassets.generated.resources.delete_technician_confirmation
import wingslog.feature.technician.sharedassets.generated.resources.edit_profile
import wingslog.feature.technician.sharedassets.generated.resources.edit_technician
import wingslog.feature.technician.sharedassets.generated.resources.name_required
import wingslog.feature.technician.sharedassets.generated.resources.technician_delete_hint
import wingslog.feature.technician.sharedassets.generated.resources.technician_email_label
import wingslog.feature.technician.sharedassets.generated.resources.technician_email_managed
import wingslog.feature.technician.sharedassets.generated.resources.technician_name_label
import wingslog.feature.technician.sharedassets.generated.resources.technician_section_details
import wingslog.feature.technician.sharedassets.generated.resources.technician_update_name
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

private val ProfileAvatarSize = 96.dp
private val FieldLabelTracking = 0.5.sp

/**
 * One editor for every person on the roster, yourself included: an avatar, a Details card, a
 * Certifications card, and — for someone other than you — the way to remove them. Your own record
 * also shows the account email, read-only, since the sign-in provider owns it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTechnicianScreen(
  viewModel: EditTechnicianViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  var showDeleteDialog by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.saveSuccess) {
    if (uiState.saveSuccess) onNavigateBack()
  }

  LaunchedEffect(uiState.deleteSuccess) {
    if (uiState.deleteSuccess) onNavigateBack()
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(TechnicianRes.string.delete_technician)) },
      text = { Text(stringResource(TechnicianRes.string.delete_technician_confirmation)) },
      confirmButton = {
        TextButton(onClick = {
          showDeleteDialog = false
          viewModel.delete()
        }) {
          Text(
            stringResource(TechnicianRes.string.delete_technician),
            color = MaterialTheme.colorScheme.error
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      }
    )
  }

  val nameDraft = uiState.nameDraft
  if (nameDraft != null) {
    RenameDialog(
      draft = nameDraft,
      onDraftChange = viewModel::updateNameDraft,
      onConfirm = viewModel::confirmNameDraft,
      onDismiss = viewModel::dismissNameEditor,
    )
  }

  val isNew = uiState.id.isEmpty()
  Scaffold(
    modifier = modifier.imePadding(),
    topBar = {
      ConstrainedTopBar(ContentWidth.Form) {
        WingsLogTopAppBar(
          title = stringResource(
            when {
              isNew -> TechnicianRes.string.add_technician
              uiState.isSelf -> TechnicianRes.string.edit_profile
              else -> TechnicianRes.string.edit_technician
            }
          ),
          onBackClick = onNavigateBack,
        )
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
    ) {
      Box(
        modifier = Modifier.weight(1f)
          .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .constrainedContentWidth(ContentWidth.Form)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenPadding),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
        ) {
          if (uiState.error != null) {
            Text(
              text = uiState.error!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }

          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            AvatarIcon(
              displayName = uiState.name.takeIf { it.isNotBlank() },
              photoUri = if (uiState.isSelf) uiState.photoUrl else null,
              size = ProfileAvatarSize,
              textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
          }

          GroupedSection(stringResource(TechnicianRes.string.technician_section_details)) {
            GroupedRowGroup(
              dividerStartInset = Spacing.xLarge,
              rows = buildList {
                add {
                  ProfileFieldRow(
                    label = stringResource(TechnicianRes.string.technician_name_label),
                    value = uiState.name,
                    placeholder = stringResource(TechnicianRes.string.name_required),
                    onEdit = viewModel::openNameEditor,
                  )
                }
                val email = uiState.email
                if (uiState.isSelf && email != null) {
                  add {
                    ProfileFieldRow(
                      label = stringResource(TechnicianRes.string.technician_email_label),
                      value = email,
                      supporting = stringResource(TechnicianRes.string.technician_email_managed),
                    )
                  }
                }
              },
            )
          }

          GroupedSection(stringResource(TechnicianRes.string.certifications)) {
            GroupedCard {
              CertificationInputFields(
                entries = uiState.certifications,
                offered = uiState.offered,
                onAdd = viewModel::addCertification,
                onAddCustom = viewModel::addCustomCertification,
                onRemove = viewModel::removeCertification,
                onNumberChanged = viewModel::updateCertificationNumber,
                onLabelChanged = viewModel::updateCertificationLabel,
                onExpireLimitChanged = viewModel::updateCertificationExpireLimit,
                onExpirationChanged = viewModel::updateCertificationExpiration,
                showHeader = false,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = Spacing.xLarge, vertical = Spacing.large),
              )
            }
          }

          // Never for yourself: your record is what signs your own work.
          if (!isNew && !uiState.isSelf) {
            DestructiveActionCard(
              icon = Icons.Default.Delete,
              title = stringResource(TechnicianRes.string.delete_technician),
              subtitle = stringResource(TechnicianRes.string.technician_delete_hint),
              onClick = { showDeleteDialog = true },
            )
          }
        }
      }

      BottomButtons(
        onPrimaryClick = viewModel::save,
        primaryEnabled = !uiState.isSaving,
        isPrimaryFunctionInProgress = uiState.isSaving,
      )
    }
  }
}

/** The name editor: one field in a dialog, so the Details card stays a record rather than a form. */
@Composable
private fun RenameDialog(
  draft: String,
  onDraftChange: (String) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(TechnicianRes.string.technician_update_name)) },
    text = {
      FormTextField(
        value = draft,
        onValueChange = onDraftChange,
        label = stringResource(TechnicianRes.string.name_required),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = onConfirm, enabled = draft.isNotBlank()) {
        Text(stringResource(CoreRes.string.save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
    },
  )
}

/**
 * A labelled value inside the Details card. With [onEdit] the row opens an editor and carries a
 * pencil; without it the value is locked (a lock glyph and a [supporting] line saying who owns it).
 */
@Composable
private fun ProfileFieldRow(
  label: String,
  value: String,
  onEdit: (() -> Unit)? = null,
  placeholder: String? = null,
  supporting: String? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
      .padding(horizontal = Spacing.xLarge, vertical = Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = FieldLabelTracking,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val empty = value.isBlank() && placeholder != null
      Text(
        text = if (empty) placeholder.orEmpty() else value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
      )
      if (supporting != null) {
        Text(
          text = supporting,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.width(Spacing.large))
    Icon(
      imageVector = if (onEdit != null) Icons.Default.Edit else Icons.Default.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = Spacing.extraSmall),
    )
  }
}
