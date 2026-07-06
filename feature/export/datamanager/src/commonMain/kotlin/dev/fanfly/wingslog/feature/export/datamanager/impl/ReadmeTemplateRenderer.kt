package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Renders the export README text template with the values for one exported aircraft.
 */
class ReadmeTemplateRenderer(
  private val template: String,
) {

  /**
   * Replaces every `{{key}}` token in the template with its matching value.
   */
  fun render(valuesByKey: Map<String, String>): String =
    valuesByKey.entries.fold(template) { rendered, (key, value) ->
      rendered.replace("{{$key}}", value)
    }
      .trimEnd() + "\n"
}
