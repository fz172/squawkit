package dev.fanfly.wingslog.feature.sharing.datamanager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A parsed aircraft-share invite carried in a `/share#{hostUid}.{aircraftId}.{secret}` deep link. */
data class ShareInvite(
  /**
   * The account whose tree the aircraft lives in. Carried because the ACL is keyed under the host
   * (#204) and an invitee has no ref yet, so there is no other way to locate the share doc.
   *
   * It is **routing, not a capability**: creating a share under a uid requires *being* that uid
   * (rules pin the path segment to the signed token), so knowing a host's uid grants nothing.
   */
  val hostUid: String,
  val aircraftId: String,
  val secret: String,
)

/**
 * Platform-agnostic channel for an inbound aircraft-share deep link, mirroring [EmailLinkDeepLinks].
 *
 * Each host pushes the URL that opened the app (Android `MainActivity`, iOS `MainEntry`, Web
 * `main.kt`); it dispatches share links here and email-link URLs to their own channel. The redeem
 * confirmation surface (P4, #132) observes [pendingInvite], drives [SharingManager.redeemInvite],
 * and calls [consume]. The secret rides in the URL **fragment** — never sent to servers — so it
 * can't leak into hosting/CDN logs. See docs/sharing §3.2/§6.4.
 */
object AircraftShareDeepLinks {
  private val _pendingInvite = MutableStateFlow<ShareInvite?>(null)

  /** Held (parked) until a consumer redeems it — survives the sign-in / guest-upgrade round trip. */
  val pendingInvite: StateFlow<ShareInvite?> = _pendingInvite.asStateFlow()

  /** True for a URL this channel owns, so a host can route it here instead of the email-link channel. */
  fun isShareLink(url: String): Boolean = parse(url) != null

  /** Parks [url]'s invite if it's a share link; ignores anything else. Returns whether it was taken. */
  fun deliver(url: String): Boolean {
    val invite = parse(url) ?: return false
    _pendingInvite.value = invite
    return true
  }

  /** Called once the parked invite has been handled so it isn't re-processed. */
  fun consume() {
    _pendingInvite.value = null
  }

  /**
   * Parse `https://host/share#{hostUid}.{aircraftId}.{secret}` → [ShareInvite], or null if [url]
   * isn't a share link. The path must be `/share`. Firebase uids, aircraft ids and base64url secrets
   * contain no `.`, so splitting on dots is unambiguous.
   *
   * Links minted before #204 carried only `{aircraftId}.{secret}`; they no longer resolve, and are
   * rejected rather than guessed at. The ACL they pointed to has moved.
   */
  fun parse(url: String): ShareInvite? {
    val beforeFragment = url.substringBefore('#')
    if (beforeFragment == url) return null // no fragment
    if (!beforeFragment.substringBefore('?').trimEnd('/').endsWith("/share")) return null
    val parts = url.substringAfter('#')
      .split('.')
    if (parts.size != 3) return null
    val (hostUid, aircraftId, secret) = parts
    if (hostUid.isBlank() || aircraftId.isBlank() || secret.isBlank()) return null
    return ShareInvite(hostUid = hostUid, aircraftId = aircraftId, secret = secret)
  }
}
