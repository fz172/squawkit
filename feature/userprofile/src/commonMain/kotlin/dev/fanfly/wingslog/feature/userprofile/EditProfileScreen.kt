@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fanfly.wingslog.feature.userprofile

// removed ExposedDropdownMenuAnchorType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.model.userprofile.LicenseExpireLimit
import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import dev.fanfly.wingslog.feature.userprofile.data.EditProfileViewModel
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCard
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCardData
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.utils.displayResId
import kotlinx.datetime.Instant
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.edit_profile
import wingslog.core.ui.generated.resources.ok
import wingslog.feature.userprofile.generated.resources.license_expiration_date
import wingslog.feature.userprofile.generated.resources.license_number
import wingslog.feature.userprofile.generated.resources.license_type
import wingslog.feature.userprofile.generated.resources.never
import wingslog.feature.userprofile.generated.resources.select_date
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreUiRes
import wingslog.feature.userprofile.generated.resources.Res as UserProfileRes


@Composable
fun EditProfileScreen(
  viewModel: EditProfileViewModel = koinViewModel(),
  navController: NavController,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // State for the dropdown menu
  var expanded by remember { mutableStateOf(false) }

  // This effect will run when isSaved becomes true
  LaunchedEffect(uiState.isSaved) {
    if (uiState.isSaved) {
      // Navigate back when save is successful
      navController.popBackStack()
    }
  }

  Scaffold(topBar = {
    WingsLogTopAppBar(
      title = cmpStringResource(CoreUiRes.string.edit_profile),
      onBackClick = { navController.popBackStack() })
  }) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      UserProfileCard(
        data = UserProfileCardData(
          displayName = uiState.displayName,
          photoUri = uiState.photoUri,
        )
      )

      // --- License Type (Dropdown) ---
      ExposedDropdownMenuBox(
        expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
          value = cmpStringResource(uiState.licenceInfo.license_type.displayResId()),
          onValueChange = {},
          readOnly = true,
          label = { Text(text = cmpStringResource(UserProfileRes.string.license_type)) },
          trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
          },
          modifier = Modifier
            .fillMaxWidth()
            .menuAnchor(),
          shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
          expanded = expanded, onDismissRequest = { expanded = false }) {
          LicenseType.entries.forEach { type ->
            DropdownMenuItem(text = { Text(cmpStringResource(type.displayResId())) }, onClick = {
              viewModel.onLicenseTypeChanged(type) // Update ViewModel
              expanded = false
            })
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // --- License Number ---
      OutlinedTextField(
        value = uiState.licenceInfo.license_number, // Read from ViewModel
        onValueChange = { viewModel.onLicenseNumberChanged(it) }, // Update ViewModel
        label = { Text(cmpStringResource(UserProfileRes.string.license_number)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        enabled = uiState.licenceInfo.license_type != LicenseType.NONE
      )

      Spacer(modifier = Modifier.height(20.dp))

      var showDatePicker by remember { mutableStateOf(false) }

      // --- Expiration Date ---
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val expirationDateEnabled =
          uiState.licenceInfo.expireLimit != LicenseExpireLimit.NEVER_EXPIRES && uiState.licenceInfo.license_type != LicenseType.NONE
        OutlinedTextField(
          value = if (uiState.licenceInfo.expireLimit != LicenseExpireLimit.NEVER_EXPIRES)
            uiState.licenceInfo.expiration_date?.toLocalDate()?.toDisplayFormat() ?: "" else "",
          onValueChange = { }, // Update ViewModel
          readOnly = true,
          label = { Text(cmpStringResource(UserProfileRes.string.license_expiration_date)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.CalendarToday,
              contentDescription = cmpStringResource(UserProfileRes.string.select_date)
            )
          },
          enabled = false,
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .clickable {
              if (expirationDateEnabled) {
                showDatePicker = true
              }
            },
          colors = if (expirationDateEnabled) {
            OutlinedTextFieldDefaults.colors(
              // Tell Compose to use the "onSurface" color (your normal text color)
              // INSTEAD of the default disabled grey color.
              disabledTextColor = MaterialTheme.colorScheme.onSurface,

              // Tell Compose to use the "outline" color (your normal border color)
              // INSTEAD of the default disabled grey border.
              disabledBorderColor = MaterialTheme.colorScheme.outline,

              // Do the same for the label
              disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,

              // ...and any other colors you need (icons, placeholders)
              disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
              disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            OutlinedTextFieldDefaults.colors()
          }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = cmpStringResource(UserProfileRes.string.never))
        Checkbox(
          checked = uiState.licenceInfo.expireLimit == LicenseExpireLimit.NEVER_EXPIRES,
          onCheckedChange = { viewModel.onExpirationNeverFlagChanged(it) },
          enabled = uiState.licenceInfo.license_type != LicenseType.NONE
        )
      }
      if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
          onDismissRequest = { showDatePicker = false },
          confirmButton = {
            TextButton(
              onClick = {
                val selectedDate = datePickerState.selectedDateMillis?.let {
                  Instant.fromEpochMilliseconds(it)
                } ?: kotlin.time.Clock.System.now()
                viewModel.onExpirationDateChanged(selectedDate)
                showDatePicker = false
              }) {
              Text(text = cmpStringResource(CoreUiRes.string.ok))
            }
          },
          dismissButton = {
            TextButton(
              onClick = { showDatePicker = false }) {
              Text(text = cmpStringResource(CoreUiRes.string.cancel))
            }
          }) {
          DatePicker(state = datePickerState)
        }
      }
      Spacer(Modifier.height(88.dp))
    }
    BottomButtons(
      modifier = Modifier.align(Alignment.BottomCenter),
      saveEnabled = !uiState.isLoading,
      onSaveClick = { viewModel.saveChanges() },
      onCancelClick = { navController.popBackStack() }
    )
  }
  }
}
