package dev.fanfly.wingslog.core.storage

/**
 * One record, by kind and id, as the entity table keys it. Ids are random per record, so the pair
 * names a record across scopes.
 */
data class EntityRef(val collection: CollectionKind, val id: String)
