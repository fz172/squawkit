package dev.fanfly.wingslog.core.ui.form

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Shared [KeyboardOptions] presets for form fields, so IME behavior stays consistent across
 * feature forms instead of each field hand-building its own options.
 */
object FormKeyboard {
  /** Prose fields. Multiline-safe: leaves the Enter key inserting newlines. */
  val Sentences =
    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

  /** Single-line prose field that advances focus to the next field. */
  val SentencesNext = Sentences.copy(imeAction = ImeAction.Next)

  /**
   * Single-line prose field whose Enter key closes input. Never use on a multiline field — it
   * replaces the Return key, so the user can no longer type line breaks.
   */
  val SentencesDone = Sentences.copy(imeAction = ImeAction.Done)

  /** Names, where every word is part of what the thing is called. Advances to the next field. */
  val WordsNext = KeyboardOptions(
    capitalization = KeyboardCapitalization.Words,
    imeAction = ImeAction.Next,
  )

  /** [WordsNext], for the last field on a row. */
  val WordsDone = WordsNext.copy(imeAction = ImeAction.Done)

  /** All-caps identifiers (reference numbers, tail numbers) that advance to the next field. */
  val CharactersNext = KeyboardOptions(
    capitalization = KeyboardCapitalization.Characters,
    imeAction = ImeAction.Next,
  )
}
