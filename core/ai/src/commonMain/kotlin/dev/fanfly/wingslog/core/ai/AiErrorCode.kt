package dev.fanfly.wingslog.core.ai

/**
 * Why an AI run was refused or failed: the backend's codes (docs/ai/task_population_design.md
 * §5.7), plus two for what never got a code. Each maps to one string and one analytics reason
 * (PRD R21, R50), so a new backend code needs an entry here before the server sends it; until
 * then it reads as [UNKNOWN].
 */
enum class AiErrorCode(val wire: String?) {
  SIGN_IN_REQUIRED("sign_in_required"),
  DISABLED("disabled"),
  NOT_MEMBER("not_member"),
  OWNER_NOT_PRO("owner_not_pro"),
  DAILY_LIMIT("daily_limit"),
  RUN_IN_PROGRESS("run_in_progress"),
  SPEND_CEILING("spend_ceiling"),
  DOCUMENT_MISSING("document_missing"),
  DOCUMENT_TOO_LARGE("document_too_large"),
  DOCUMENT_UNREADABLE("document_unreadable"),
  NO_SCHEDULE_FOUND("no_schedule_found"),
  PROVIDER_ERROR("provider_error"),
  INVALID_OUTPUT("invalid_output"),
  STALE("stale"),

  /** No answer from the server: offline, or a request that never left the device. */
  UNAVAILABLE(null),

  /**
   * The server would not accept this copy of the app: App Check could not attest it, which on a
   * developer build means its debug token is not registered. Not the user's to fix.
   */
  APP_UNVERIFIED(null),

  /** A code this build does not know, or a refusal that carried none. */
  UNKNOWN(null),
  ;

  companion object {
    fun fromWire(code: String?): AiErrorCode =
      if (code == null) UNKNOWN else entries.firstOrNull { it.wire == code }
        ?: UNKNOWN
  }
}
