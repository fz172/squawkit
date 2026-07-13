package dev.fanfly.wingslog.feature.sharing.model

/**
 * Pairing-code format (#164). Must agree with `inviteCodes.ts` on the server — the alphabet is what
 * makes a code unambiguous read aloud in a hangar, and the length is what the entropy argument rests
 * on.
 */
private const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"
private const val CODE_LENGTH = 8

/**
 * Accepts what a human actually types — lowercase, spaces, and the `EFA1-GGTH` grouping we display —
 * and returns the canonical code, or null if the input is not one.
 *
 * Separators are stripped; **anything else outside the alphabet is a rejection, not a deletion.**
 * Silently dropping stray characters was a real bug: a legacy `#ac-1.sEcReT` link filtered down to
 * `ACSECRET`, a perfectly well-formed code that happened to be nonsense. Refusing is honest — a
 * character we do not recognise means the input was not a code.
 *
 * `0/O`, `1/I/L` and `U` are not in the alphabet at all, which is why a code survives being read
 * aloud without spelling it.
 */
fun normalizeInviteCode(input: String): String? {
  val cleaned = input.uppercase()
    .filterNot { it == '-' || it.isWhitespace() }
  if (cleaned.length != CODE_LENGTH) return null
  return if (cleaned.all { it in ALPHABET }) cleaned else null
}

/** `EFA1GGTH` → `EFA1-GGTH`. Display only; the code itself is unformatted. */
fun formatInviteCode(code: String): String =
  if (code.length == CODE_LENGTH) "${code.take(4)}-${code.drop(4)}" else code

/**
 * Where a share link points. The fragment is the code and nothing else — the URL names no aircraft
 * and no host, which is the whole reason the mechanism changed (#202/#204).
 */
const val SHARE_URL_BASE: String = "https://squawkit.fanfly.dev/share"
