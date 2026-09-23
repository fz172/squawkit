package dev.fanfly.wingslog.feature.settings

/**
 * The thing a pilot has to type before "Delete my account" does anything (#418).
 *
 * A single tap on a destructive button is too cheap for an irreversible, un-undoable delete that
 * also cuts off everyone they have shared a thing with — so the confirmation asks for something
 * only someone who means it will produce.
 */
sealed interface DeletionChallenge {
  /** The account has an address the pilot would recognise, so that address is what they type. */
  data class Email(val address: String) : DeletionChallenge

  /**
   * No address worth asking for — a provider gave us none, or it is an Apple Hide My Email alias
   * the pilot has never seen. They type a fixed phrase instead; the dialog owns its wording, since
   * it is a localized string.
   */
  data object Phrase : DeletionChallenge
}
