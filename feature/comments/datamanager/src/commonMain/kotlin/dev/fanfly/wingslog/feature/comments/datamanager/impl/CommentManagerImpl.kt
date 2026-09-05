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
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.Comment
import dev.fanfly.wingslog.thing.CommentParentType
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.time.Clock
import kotlin.time.Instant

class CommentManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  private val currentUid: CurrentUidProvider,
  private val technicianManager: TechnicianManager,
  private val sharingManager: SharingManager,
  private val auth: FirebaseAuth,
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
          combine(
            store.observeAll(scope),
            authorPhotos(target.thingId)
          ) { rows, photos ->
            val me = currentUid.currentUid()
            rows.asSequence()
              .map { it.value }
              .filter { it.parent_id == target.parentId && it.parent_type == target.kind.wire }
              // The store orders by local wall clock; a thread has to read in the order it was
              // written, which is what created_at records.
              .sortedBy { it.created_at?.toInstant() ?: Instant.DISTANT_PAST }
              .map { it.toEntry(me, photos) }
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
    // A tombstone is final — reviving one would erase the record of the deletion.
    check(existing.deleted_at == null) { "Comment $commentId is deleted" }
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

  /**
   * Tombstones the comment: clears the text and stamps `deleted_at`, leaving the row in place.
   *
   * Deliberately not `store.delete`. An edit is visible as an "Edited …" line, so a hard delete
   * would let an author launder a rewrite by deleting and re-posting. See comment.proto.
   */
  override suspend fun deleteComment(
    target: CommentTarget,
    commentId: String,
  ): Result<Unit> = runCatching {
    val scope = scopeResolver.resolveNow(target.thingId)
    val existing = mine(scope, commentId)
    if (existing.deleted_at != null) return@runCatching
    store.put(
      commentId,
      existing.copy(
        text = "",
        deleted_at = Clock.System.now()
          .toWireInstant(),
      ),
      scope,
    )
  }.onFailure { logger.w(it) { "Error deleting comment $commentId" } }

  override suspend fun deleteThread(target: CommentTarget): Result<Unit> =
    runCatching {
      val scope = scopeResolver.resolveNow(target.thingId)
      store.observeAll(scope)
        .first()
        .asSequence()
        .map { it.value }
        .filter { it.parent_id == target.parentId && it.parent_type == target.kind.wire }
        .forEach { store.delete(it.id, scope) }
    }.onFailure { logger.w(it) { "Error deleting comment thread for ${target.parentId}" } }

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
   * The in-app profile name first, then the auth account's name, then its email — the same
   * precedence `SharingManagerImpl.publishTechnicianMirror` uses for the share roster, so a
   * commenter is bylined the way the roster already shows them. Denormalized at post time, so a
   * blank here would be a permanent "Unknown"; the fallbacks are what make that rare.
   */
  private suspend fun selfDisplayName(): String {
    val self = technicianManager.observeSelf()
      .first()
    val user = auth.currentUser
    return self?.name?.takeIf { it.isNotBlank() }
      ?: user?.displayName?.takeIf { it.isNotBlank() }
      ?: user?.email.orEmpty()
  }

  /**
   * Author uid → photo URL from the share roster. Starts empty so the thread never waits on the
   * roster (Firestore-backed, online-only): comments render at once and photos fill in.
   */
  private fun authorPhotos(thingId: String): Flow<Map<String, String>> =
    sharingManager.observeShareState(thingId)
      .map { state ->
        state.members
          .mapNotNull { m ->
            m.photoUrl?.takeIf { it.isNotBlank() }
              ?.let { m.uid to it }
          }
          .toMap()
      }
      .catch { emit(emptyMap()) }
      .onStart { emit(emptyMap()) }
      .distinctUntilChanged()

  private fun Comment.toEntry(
    me: String?,
    photos: Map<String, String>
  ): CommentEntry = CommentEntry(
    id = id,
    authorName = author_name,
    // Your own rows use the account photo the shell shows, which is fresher than the roster copy
    // and present even on an unshared thing that has no roster at all.
    authorPhotoUrl = auth.currentUser?.photoURL?.takeIf { author_uid == me && it.isNotBlank() }
      ?: photos[author_uid],
    text = text,
    createdAt = created_at?.toInstant() ?: Instant.DISTANT_PAST,
    editedAt = edited_at?.toInstant(),
    deletedAt = deleted_at?.toInstant(),
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
