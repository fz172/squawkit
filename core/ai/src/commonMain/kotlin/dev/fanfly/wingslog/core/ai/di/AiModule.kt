package dev.fanfly.wingslog.core.ai.di

import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.impl.FirebaseAiJobClient
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The AI backend client. [FirebaseFirestore] comes from the sync module and [FirebaseAuth] from
 * the auth module, both resolved lazily, so this module's place in `commonAppModules` does not
 * matter.
 */
val aiModule: Module = module {
  single<AiJobClient> {
    FirebaseAiJobClient(
      functions = get<FirebaseFunctions>(),
      firestore = get<FirebaseFirestore>(),
      auth = get<FirebaseAuth>(),
    )
  }
}
