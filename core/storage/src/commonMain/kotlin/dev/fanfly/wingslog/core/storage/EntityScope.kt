package dev.fanfly.wingslog.core.storage

/**
 * Identifies a collection's location in the user's data tree.
 *
 * Examples
 * - `EntityScope.userRoot("u123")` → `/users/u123/` — top-level collections like aircraft.
 * - `EntityScope.aircraftChild("u123", "ac1")` → `/users/u123/aircraft/ac1/` — nested collections
 *   like maintenance logs.
 *
 * The path string is what gets persisted in `entity.scope_path`. Two scopes with different
 * `segments` are isolated even if they share a prefix.
 */
data class EntityScope(val segments: List<String>) {
  fun toPath(): String =
    segments.joinToString(separator = "/", prefix = "/", postfix = "/")

  companion object {
    fun userRoot(uid: String): EntityScope = EntityScope(listOf("users", uid))

    fun aircraftChild(uid: String, aircraftId: String): EntityScope =
      EntityScope(listOf("users", uid, "aircraft", aircraftId))
  }
}
