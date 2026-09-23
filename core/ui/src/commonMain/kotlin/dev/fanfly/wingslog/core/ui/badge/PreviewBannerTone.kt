package dev.fanfly.wingslog.core.ui.badge

import androidx.compose.foundation.border

/**
 * Read-only informational banner for summarising a multi-step form's current state.
 *
 * Styled to look unlike an input: a faint tint of the tone with a hairline border of the same
 * colour, and nothing to press. The tone rides the tint and the label — never a side stripe, which
 * `DESIGN.md` rules out as a callout accent.
 *
 * Use [PreviewBannerTone] to communicate semantic state:
 * - [PreviewBannerTone.Neutral] — nothing configured yet, or no change from default
 * - [PreviewBannerTone.Active] — form has meaningful input; showing a live preview
 * - [PreviewBannerTone.Warn]   — a cycle-skip or destructive adjustment is pending (amber)
 */
enum class PreviewBannerTone { Neutral, Active, Warn }
