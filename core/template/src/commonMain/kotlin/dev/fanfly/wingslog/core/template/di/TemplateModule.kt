package dev.fanfly.wingslog.core.template.di

import dev.fanfly.wingslog.core.template.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.template.TemplateRegistry
import org.koin.dsl.module

/**
 * The registry is a singleton holding immutable data — one baked-in preset, no cache, no fetch —
 * so there is nothing per-scope about it and nothing to invalidate.
 */
val templateModule = module {
  single<TemplateRegistry> { BakedInTemplateRegistry() }
}
