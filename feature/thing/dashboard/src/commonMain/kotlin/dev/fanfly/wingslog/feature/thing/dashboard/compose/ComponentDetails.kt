package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.thing.Component
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.make_model_template
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * One component, whatever slot it fills (#729).
 *
 * Was `EngineDetails`, which drew an engine and then reached *inside* it for a propeller, its hub
 * and its blades — a fixed four levels of aviation nested in one composable. The caller now walks
 * `componentRows`, which emits every component at every depth, so each one renders here flat and
 * the nesting lives in the walk instead of in this file.
 */
@Composable
fun ComponentDetails(
  label: String,
  component: Component,
) {
  ComponentCard(
    category = label,
    name = stringResource(
      CoreRes.string.make_model_template,
      component.make,
      component.model,
    ),
    serial = component.serial,
  )
}
