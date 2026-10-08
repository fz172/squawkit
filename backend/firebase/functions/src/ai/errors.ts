import type { AiUsage } from "./providers/types.js";

/**
 * Error codes an AI job can end with (design §5.7). The client maps each to one string and one
 * analytics reason, so a new code needs a client string before it ships.
 */
export type AiErrorCode =
  | "sign_in_required"
  | "disabled"
  | "not_member"
  | "owner_not_pro"
  | "daily_limit"
  | "run_in_progress"
  | "spend_ceiling"
  | "document_missing"
  | "document_too_large"
  | "document_unreadable"
  | "no_schedule_found"
  | "provider_error"
  | "invalid_output"
  | "stale";

/**
 * A failure the job reports by code. `detail` is for logs only, never shown to the user. `usage` is
 * what was spent before failing, so the cost log still counts it.
 */
/**
 * The job was closed (accepted or dismissed) while it ran. Not a failure: there is no job left to
 * write an outcome to and no one to tell, so the run only stops spending.
 */
export class AiJobClosedError extends Error {
  constructor() {
    super("the job was closed while it ran");
    this.name = "AiJobClosedError";
  }
}

export class AiError extends Error {
  readonly usage?: AiUsage;

  constructor(
    readonly code: AiErrorCode,
    readonly detail: string,
    options?: { cause?: unknown; usage?: AiUsage },
  ) {
    super(`${code}: ${detail}`, { cause: options?.cause });
    this.name = "AiError";
    this.usage = options?.usage;
  }
}
