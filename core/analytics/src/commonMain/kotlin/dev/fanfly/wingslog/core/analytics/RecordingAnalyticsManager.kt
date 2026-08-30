package dev.fanfly.wingslog.core.analytics

/**
 * An [AnalyticsManager] that keeps what it was told, for tests that assert an event was emitted.
 *
 * Lives in `commonMain` rather than a test source set so every feature module can use it without
 * each one re-deriving the same fake — the alternative is a relaxed mock per call site, which
 * records nothing and therefore cannot answer the question these tests exist to ask.
 *
 * Firebase DebugView (#667) proves an event *arrives*; this proves we *emit* it, and on the right
 * branch. Neither substitutes for the other: an event emitted on the edit path as well as the create
 * path lands in GA4 perfectly and still makes the §13 Things-per-account metric wrong.
 */
class RecordingAnalyticsManager : AnalyticsManager {

  /** Every event, in order, as the name and flattened params that reached the SDK boundary. */
  val events: List<Pair<String, Map<String, String>>> get() = _events

  /** Screen views, kept separately so an event assertion is not perturbed by navigation. */
  val screenViews: List<String> get() = _screenViews

  var collectionEnabled: Boolean? = null
    private set

  private val _events = mutableListOf<Pair<String, Map<String, String>>>()
  private val _screenViews = mutableListOf<String>()

  override fun logScreenView(screenName: String, params: Map<String, String>) {
    _screenViews += screenName
  }

  override fun logEvent(name: String, params: Map<String, String>) {
    _events += name to params
  }

  override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
    collectionEnabled = enabled
  }

  fun clear() {
    _events.clear()
    _screenViews.clear()
  }

  /** The params of every event with [name], for asserting a property such as `template_id`. */
  fun paramsFor(name: String): List<Map<String, String>> =
    _events.filter { it.first == name }.map { it.second }

  /** How many times [name] was emitted — the shape most of these assertions want. */
  fun countOf(name: String): Int = _events.count { it.first == name }
}
