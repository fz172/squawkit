package dev.fanfly.wingslog.core.template.di

import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import org.koin.dsl.module

/**
 * The registry is a singleton holding immutable data — one baked-in preset, no cache, no fetch —
 * so there is nothing per-scope about it and nothing to invalidate.
 */
val templateModule = module {
  single<TemplateRegistry> { BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE) }
  // App-scoped on purpose: the root providers and the shell ViewModel must see one
  // instance, or the form dialogs read a lexicon nobody is updating.
  single<CurrentThingTemplate> { CurrentThingTemplate(get<TemplateRegistry>()) }
}
