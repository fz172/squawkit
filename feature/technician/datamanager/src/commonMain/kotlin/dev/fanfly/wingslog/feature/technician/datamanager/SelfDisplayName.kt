package dev.fanfly.wingslog.feature.technician.datamanager

import dev.fanfly.wingslog.thing.Technician
import dev.gitlive.firebase.auth.FirebaseUser

/**
 * The signed-in user's name as the app shows it: the self-technician name they edit in the app,
 * then the auth account's display name, then its email. Null when none is set.
 *
 * Every surface that names the current user (sidebar account row, share roster, comment bylines,
 * the member-doc mirror) resolves through here so they cannot disagree.
 */
fun selfDisplayName(self: Technician?, user: FirebaseUser?): String? =
  self?.name?.takeIf { it.isNotBlank() }
    ?: user?.displayName?.takeIf { it.isNotBlank() }
    ?: user?.email?.takeIf { it.isNotBlank() }
