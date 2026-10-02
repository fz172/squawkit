package dev.fanfly.wingslog.core.firebase.functions

import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.details
import platform.Foundation.NSDictionary

// gitlive passes the NSError's userInfo["details"] through untouched, an NSDictionary. A bridged
// Kotlin Map is accepted too, in case a later SDK converts it.
actual fun FirebaseFunctionsException.detailsString(key: String): String? =
  when (val d = details) {
    is Map<*, *> -> d[key]
    is NSDictionary -> d.objectForKey(key)
    else -> null
  } as? String
