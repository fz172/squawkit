package dev.fanfly.wingslog.feature.comments.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toInstant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.CurrentUidProvider
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.model.CommentEntry
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.Comment
import dev.fanfly.wingslog.thing.CommentParentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

class CommentManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  private val currentUid: CurrentUidProvider,
  private val technicianManager: TechnicianManager,
  storeFactory: EntityStoreFactory,
) : CommentManager {

  private val store: EntityStore<Comment> =
    storeFactory.create(CollectionKind.Comment)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeComments(target: CommentTarget): Flow<List<CommentEntry>> =
    scopeResolver.resolve(target.thingId)
      .flatMapLatest { scope ->
        if (scope == null) {
          flowOf(emptyList())
        } else {
          store.observeAll(scope)
            .map { rows ->
              val me = currentUid.currentUid()
              rows.asSequence()
                .map { it.value }
                .filter { it.parent_id == target.parentId && it.parent_type == target.kind.wire }
                // The store orders by local wall clock; a thread has to read in the order it was
                // written, which is what created_at records.
                .sortedBy { it.created_at?.toInstant() ?: Instant.DISTANT_PAST }
                .map { it.toEntry(me) }
                .toList()
            }
            .catch { e ->
              logger.w(e) { "Error observing comments for ${target.parentId}" }
              emit(emptyList())
            }
        }
      }

  override suspend fun addComment(
    target: CommentTarget,
    text: String,
  ): Result<Unit> = runCatching {
    val body = text.trim()
    if (body.isEmpty()) return@runCatching
    val scope = scopeResolver.resolveNow(target.thingId)
    val id = generateRandomId()
    store.put(
      id,
      Comment(
        id = id,
        parent_id = target.parentId,
        parent_type = target.kind.wire,
        text = body,
        author_uid = currentUid.currentUid()
          .orEmpty(),
        author_name = selfDisplayName(),
        created_at = Clock.System.now()
          .toWireInstant(),
      ),
      scope,
    )
  }.onFailure { logger.w(it) { "Error adding comment to ${target.parentId}" } }

  override suspend fun updateComment(
    target: CommentTarget,
    commentId: String,
    text: String,
  ): Result<Unit> = runCatching {
    val body = text.trim()
    if (body.isEmpty()) return@runCatching
    val scope = scopeResolver.resolveNow(target.thingId)
    val existing = mine(scope, commentId)
    if (existing.text == body) return@runCatching
    store.put(
      commentId,
      existing.copy(
        text = body,
        edited_at = Clock.System.now()
          .toWireInstant(),
      ),
      scope,
    )
  }.onFailure { logger.w(it) { "Error updating comment $commentId" } }

  override suspend fun deleteComment(
    target: CommentTarget,
    commentId: String,
  ): Result<Unit> = runCatching {
    val scope = scopeResolver.resolveNow(target.thingId)
    mine(scope, commentId)
    store.delete(commentId, scope)
  }.onFailure { logger.w(it) { "Error deleting comment $commentId" } }

  /**
   * The stored comment, or a failure if this account did not write it.
   *
   * The UI only offers Edit and Delete on your own comments, so reaching here with someone else's
   * is a bug — but a shared thread is exactly where "only the UI checks" stops being enough.
   */
  private suspend fun mine(scope: EntityScope, commentId: String): Comment {
    val existing = store.observe(commentId, scope)
      .firstOrNull()
      ?.value
      ?: error("Comment $commentId not found")
    val me = currentUid.currentUid()
    check(me != null && existing.author_uid == me) {
      "Comment $commentId was not written by this account"
    }
    return existing
  }

  /**
   * The in-app profile name, which is what the author edits and expects to see — the same
   * precedence `SharingManagerImpl.publishTechnicianMirror` uses for the share roster.
   */
  private suspend fun selfDisplayName(): String =
    technicianManager.observeSelf()
      .first()
      ?.name
      ?.takeIf { it.isNotBlank() }
      .orEmpty()

  private fun Comment.toEntry(me: String?): CommentEntry = CommentEntry(
    id = id,
    authorName = author_name,
    text = text,
    createdAt = created_at?.toInstant() ?: Instant.DISTANT_PAST,
    editedAt = edited_at?.toInstant(),
    isMine = me != null && author_uid == me,
  )

  companion object {
    private val logger = Logger.withTag("CommentManager")
  }
}

internal val CommentParentKind.wire: CommentParentType
  get() = when (this) {
    CommentParentKind.SQUAWK -> CommentParentType.COMMENT_PARENT_TYPE_SQUAWK
    CommentParentKind.MAINTENANCE_TASK ->
      CommentParentType.COMMENT_PARENT_TYPE_MAINTENANCE_TASK
  }
