package dev.fanfly.wingslog.feature.datalog.datamanager.csv

/**
 * The recorder's file as text.
 *
 * Platform-specific for one reason, and it is a large one. `ByteArray.decodeToString` on the web
 * build is a hand-written UTF-8 decoder in the Kotlin standard library that appends to a
 * `StringBuilder` a character at a time. On a 46 MB download a profile put it at **2.8 seconds**,
 * two thirds of that inside the final `toString` and another fifth in the garbage it made — the
 * whole of the freeze between tapping a log and the viewer drawing. The same call takes 10 ms on
 * the JVM, which is why it was invisible until someone recorded the browser doing it.
 *
 * Invalid bytes never throw. A recorder's file is not ours to reject over one bad sequence, and
 * every parser here reads it line by line anyway.
 */
internal expect fun decodeText(bytes: ByteArray): String
