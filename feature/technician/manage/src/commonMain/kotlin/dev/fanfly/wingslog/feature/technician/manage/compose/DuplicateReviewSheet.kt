package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateGroup
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateResolution
import androidx.compose.foundation.layout.Row
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_merge_member
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_apply
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_merge_manual
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_needs_confirmation
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_no_selection
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_replaces
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_review_body
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_review_title
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_warn_mirror

/**
 * Lets the user reconcile look-alike roster rows (design §7.4).
 *
 * Nothing is applied silently, and a merge deletes roster rows, so the checkboxes matter.
 * Certificate-number matches arrive pre-checked because a certificate identifies one person;
 * name-only matches start unchecked and say so, because names collide. Mirror↔mirror conflicts are
 * shown as a warning with no checkbox — two members are two accounts, nothing to merge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateReviewSheet(
  groups: List<DuplicateGroup>,
  onApply: (List<DuplicateGroup>) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val mergeable = groups.filter { it.resolution != DuplicateResolution.WARN_MIRROR_CONFLICT }
  val warnings = groups.filter { it.resolution == DuplicateResolution.WARN_MIRROR_CONFLICT }

  // Keyed by the kept row's id: pre-checked only where the match key is strong enough to trust.
  val checked = remember(groups) {
    mutableStateMapOf<String, Boolean>().apply {
      mergeable.forEach { put(it.keep.id, it.autoSafe) }
    }
  }

  PickerSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    headerSlot = {
      Text(
        text = stringResource(Res.string.duplicates_review_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(Res.string.duplicates_review_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      mergeable.forEach { group ->
        MergeRow(
          group = group,
          checked = checked[group.keep.id] == true,
          onCheckedChange = { checked[group.keep.id] = it },
        )
      }

      warnings.forEach { group ->
        WarningCard(group)
      }

      val selected = mergeable.filter { checked[it.keep.id] == true }
      Button(
        onClick = { onApply(selected) },
        enabled = selected.isNotEmpty(),
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = Spacing.small),
      ) {
        Text(
          if (selected.isEmpty()) stringResource(Res.string.duplicates_no_selection)
          else stringResource(Res.string.duplicates_apply)
        )
      }
    }
  }
}

@Composable
private fun MergeRow(
  group: DuplicateGroup,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  val title = when (group.resolution) {
    DuplicateResolution.MERGE_INTO_MEMBER ->
      stringResource(Res.string.duplicates_merge_member, group.keep.name)

    else -> stringResource(Res.string.duplicates_merge_manual, group.keep.name)
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = stringResource(
          Res.string.duplicates_replaces,
          group.duplicates.joinToString(", ") { it.name },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!group.autoSafe) {
        Text(
          text = stringResource(Res.string.duplicates_needs_confirmation),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun WarningCard(group: DuplicateGroup) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ),
  ) {
    Text(
      text = stringResource(
        Res.string.duplicates_warn_mirror,
        group.keep.name,
        group.duplicates.joinToString(", ") { it.name },
      ),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(Spacing.medium),
    )
  }
}
