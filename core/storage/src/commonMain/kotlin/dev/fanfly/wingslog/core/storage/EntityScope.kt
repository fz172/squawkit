package dev.fanfly.wingslog.core.storage

/**
 * Identifies a collection's location in the user's data tree.
 *
 * Examples
 * - `EntityScope.userRoot("u123")` → `/users/u123/` — top-level collections like aircraft.
 * - `EntityScope.aircraftChildUnsafe("u123", "ac1")` → `/users/u123/aircraft/ac1/` — nested
 *   collections like maintenance logs.
 *
 * The path string is what gets persisted in `entity.scope_path`. Two scopes with different
 * `segments` are isolated even if they share a prefix.
 */
data class EntityScope(val segments: List<String>) {
  fun toPath(): String =
    segments.joinToString(separator = "/", prefix = "/", postfix = "/")

  companion object {
    fun userRoot(uid: String): EntityScope = EntityScope(listOf("users", uid))

    /**
     * The scope of an aircraft's nested data (logs, tasks, squawks, blobs) at `[uid]`.
     *
     * **`Unsafe` because [uid] is taken at face value.** For a *shared* aircraft the data lives in
     * the HOST's tree, not the caller's — so passing the signed-in user's uid here silently writes
     * to the wrong tree: the write "succeeds" against the caller's own tree, the host never sees it,
     * and the host's read 404s. This exact mistake stranded a technician's attachment upload
     * (see [dev.fanfly.wingslog.core.storage.AircraftScopeResolver]).
     *
     * **Feature-layer per-aircraft code must NOT call this.** Inject [AircraftScopeResolver] and use
     * `resolveNow(aircraftId)` / `resolve(aircraftId)`, which returns the caller's tree for an owned
     * aircraft and the host's tree for a shared one.
     *
     * Direct use is legitimate only where the owning uid is already known-correct: the resolver's
     * own implementation, and the sync engine's fan-out (which iterates own aircraft under
     * `userRoot` and derives shared scopes from each `SharedAircraftRef.host_uid`).
     */
    fun aircraftChildUnsafe(uid: String, aircraftId: String): EntityScope =
      EntityScope(listOf("users", uid, "aircraft", aircraftId))
  }
}
