package dev.fanfly.wingslog.core.firebase.functions

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The region every Cloud Function in this project is deployed to.
 *
 * One constant, because it used to be five: `REGION` in two places, `FUNCTIONS_REGION` in a third,
 * a constructor default in a fourth, and an inline string in a fifth. All the same value, and all
 * of them would have had to be found and changed together to move a region — the kind of edit that
 * looks done after the first four.
 *
 * Must match `FUNCTION_REGION` in `backend/firebase/functions/src/config/env.ts`. A client pointed
 * at the wrong region does not fail at build time; it fails as a `not-found` at the call.
 */
const val FUNCTIONS_REGION = "us-central1"

/**
 * The shared Cloud Functions client.
 *
 * A single instance, injected, rather than each caller building its own from `Firebase.functions(…)`
 * — that is what let the region spellings drift apart, and it also made every callable client
 * untestable without a live Firebase.
 */
val functionsModule: Module = module {
  single<FirebaseFunctions> { Firebase.functions(FUNCTIONS_REGION) }
}
