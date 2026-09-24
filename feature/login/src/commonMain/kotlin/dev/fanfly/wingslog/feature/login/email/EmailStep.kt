package dev.fanfly.wingslog.feature.login.email

internal enum class EmailStep {
  /** Entering the address to send a link to. */
  Enter,

  /** A link was just sent; waiting for the user to open it. */
  Sent,

  /** A link was opened (this or another device); completing leg 2, prompting for email if needed. */
  Finish,
}
