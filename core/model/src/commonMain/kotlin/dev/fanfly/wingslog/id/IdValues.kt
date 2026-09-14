package dev.fanfly.wingslog.id

/**
 * Wire renames a proto field called `value` to `value_` because `value` is a Kotlin soft keyword.
 * These keep the schema's name at the call sites that unbox an id at the grandfathered string edge.
 */
val ThingId.value: String get() = value_
val DataLogId.value: String get() = value_
val UserId.value: String get() = value_
