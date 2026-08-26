package dev.fanfly.wingslog.feature.notifications.datamanager

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * A stable identifier for this physical device/install, persisted in the `device_config` table
 * (design §7.1). It is what the push token doc is keyed on
 * (`users/{uid}/push_devices/{installationId}`) — deliberately not the token itself, which
 * rotates, and not the signed-in uid, which changes on a shared device.
 *
 * `sync_config` (uid-keyed) is not the precedent here — see the `device_config` table comment in
 * Schema.sq. `LastScanStore` is the shape to copy: a small class wrapping `db.schemaQueries`
 * directly, living with its one consumer rather than in `core/storage`.
 */
class InstallIdStore(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  /**
   * Reads the persisted id, or mints and persists a new one on first call. `generateRandomId()`
   * (20-char alphanumeric) rather than a UUID type: it is already collision-resistant, and no
   * multiplatform UUID generator exists elsewhere in the tree to prefer over it.
   */
  suspend fun getOrCreate(): String {
    val existing = db.schemaQueries.selectDeviceConfig(KEY_INSTALL_ID).awaitAsOneOrNull()
    if (existing != null) return existing

    val minted = generateRandomId()
    // Re-check inside the lock: two callers racing getOrCreate() before either has written must
    // not each mint a different id and stomp the other's — the second write would silently orphan
    // the first id's Firestore doc if the two had already diverged.
    return writeLock.withLock {
      val winner = db.schemaQueries.selectDeviceConfig(KEY_INSTALL_ID).awaitAsOneOrNull()
      if (winner != null) return@withLock winner
      db.schemaQueries.upsertDeviceConfig(KEY_INSTALL_ID, minted)
      minted
    }
  }

  private companion object {
    const val KEY_INSTALL_ID = "install_id"
  }
}
