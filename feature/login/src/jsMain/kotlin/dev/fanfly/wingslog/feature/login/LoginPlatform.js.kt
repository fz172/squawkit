package dev.fanfly.wingslog.feature.login

// Web has no in-place guest upgrade path yet, so don't offer anonymous sign-in here.
internal actual val isAnonymousLoginSupported: Boolean = false
