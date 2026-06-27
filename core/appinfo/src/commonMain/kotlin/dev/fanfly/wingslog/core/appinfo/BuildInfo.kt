package dev.fanfly.wingslog.core.appinfo

/**
 * Build-provenance facts the shared UI can branch on. Supplied per platform at Koin startup.
 *
 * [isDeveloperBuild] is true for debug builds and the dogfood flavor — i.e. anything that isn't the
 * shipping release. Gate developer-only surfaces (e.g. the Feature Lab settings entry) on it.
 */
data class BuildInfo(val isDeveloperBuild: Boolean)
