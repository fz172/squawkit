package dev.fanfly.wingslog.feature.sync.data.blob

/**
 * True when [this] means "there is no object at that path", as opposed to "we could not reach it".
 *
 * The distinction is the whole of #426. A download that fails because the network dropped must be
 * retried; one that fails because Storage answered 404 must not — the object is not coming back,
 * and retrying it forever kept a dead blob waking WorkManager and stalling every export that
 * touched it.
 *
 * Per-platform because each SDK reports it differently, and matching on message text is the kind of
 * check that breaks silently on an SDK upgrade — the failure mode being "we retry forever again",
 * which is invisible.
 */
internal expect fun Throwable.isRemoteObjectMissing(): Boolean
