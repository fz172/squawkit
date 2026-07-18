package dev.fanfly.wingslog.feature.sharing.datamanager

import dev.fanfly.wingslog.feature.sharing.model.normalizeInviteCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A pairing code carried in a `/share#{code}` deep link (#164).
 *
 * Note what is NOT here: the aircraft id and the host uid. Older links carried both, and an aircraft
 * id turned out to be a capability — anyone holding one could fabricate a same-id aircraft in their
 * own tree and read the victim's ACL, roster, and technician certificate numbers (#202), or re-claim
 * an abandoned share outright (#204). The code names nothing real. Only the server can dereference
 * it, and it dies on first use.
 */
data class ShareInvite(val code: String)

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

  /**
   * Parks a hand-typed code (#209), sending it down the exact same redeem path a link takes — the
   * manual-entry screen is just another way to fill this channel. [raw] is normalized the way a
   * link's fragment is; a value that isn't a well-formed code is refused (returns false), so the
   * caller can keep the screen open rather than parking nonsense that would surface an error sheet.
   */
  fun deliverCode(raw: String): Boolean {
    val code = normalizeInviteCode(raw) ?: return false
    _pendingInvite.value = ShareInvite(code)
    return true
  }

  /** Called once the parked invite has been handled so it isn't re-processed. */
  fun consume() {
    _pendingInvite.value = null
  }

  /**
   * Parse `https://host/share#{code}` → [ShareInvite], or null if [url] isn't a share link.
   *
   * The code is normalized the way a typed one is, so a link and a hand-typed code go down exactly
   * one path. Normalization is strict: a character outside the alphabet is a rejection, not a
   * deletion — which is what makes legacy `{aircraftId}.{secret}` links fail cleanly instead of
   * filtering down into a well-formed but meaningless code.
   */
  fun parse(url: String): ShareInvite? {
    val beforeFragment = url.substringBefore('#')
    if (beforeFragment == url) return null // no fragment
    if (!beforeFragment.substringBefore('?')
        .trimEnd('/')
        .endsWith("/share")
    ) return null
    val code = normalizeInviteCode(url.substringAfter('#'))
    return if (code == null) null else ShareInvite(code)
  }
}
