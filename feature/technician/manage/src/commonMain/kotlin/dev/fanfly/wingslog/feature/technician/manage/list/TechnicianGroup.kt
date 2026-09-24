package dev.fanfly.wingslog.feature.technician.manage.list

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.ui.grouped.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.grouped.GroupedSection
import dev.fanfly.wingslog.thing.Technician

/** One labelled card of roster rows. */
@Composable
internal fun TechnicianGroup(
  title: String,
  technicians: List<Technician>,
  offered: List<OfferedCertification>,
  onClick: (Technician) -> Unit,
  photoFor: (Technician) -> String?,
  selfId: String? = null,
  isLinked: Boolean = false,
) {
  GroupedSection(title) {
    GroupedRowGroup(
      rows = technicians.map { technician ->
        {
          TechnicianRow(
            technician = technician,
            offered = offered,
            onClick = { onClick(technician) },
            photoUri = photoFor(technician),
            isSelf = technician.id == selfId,
            isLinked = isLinked,
          )
        }
      },
    )
  }
}
