package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.datamanager.FileByteReader
import dev.fanfly.wingslog.feature.datalog.datamanager.avidyne.AvidyneParser
import dev.fanfly.wingslog.feature.datalog.datamanager.dynon.DynonParser
import dev.fanfly.wingslog.feature.datalog.datamanager.garmin.GarminParser
import dev.fanfly.wingslog.feature.datalog.datamanager.impl.DataLogImporterImpl
import dev.fanfly.wingslog.feature.datalog.datamanager.impl.DataLogManagerImpl
import dev.fanfly.wingslog.feature.datalog.datamanager.impl.TemplateThingIdentifierLookup
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import org.koin.core.module.Module
import org.koin.dsl.module

/** Every format this build reads, in sniff order. */
private val dataLogParsers: List<DataLogParser> = listOf(GarminParser(), DynonParser(), AvidyneParser())

val dataLogDataManagerModule: Module = module {
  includes(platformChartLayoutStoreModule)
  single<DataLogCache> { DataLogCache() }
  single<HeaderSniffer> { HeaderSniffer(dataLogParsers) }
  single<TemplateThingIdentifierLookup> {
    TemplateThingIdentifierLookup(get<FleetManager>(), get<TemplateRegistry>())
  }
  single<ThingIdentifierLookup> { get<TemplateThingIdentifierLookup>() }
  single<OtherThingLookup> { get<TemplateThingIdentifierLookup>() }
  single<DataLogImporter> {
    DataLogImporterImpl(
      fileByteReader = get<FileByteReader>(),
      sniffer = get<HeaderSniffer>(),
      scopeResolver = get<ThingScopeResolver>(),
      store = get<EntityStoreFactory>().create<DataLog>(CollectionKind.DataLog),
      blobs = get<LocalBlobStore>(),
      scheduler = getOrNull<UploadScheduler>(),
      identifiers = get<ThingIdentifierLookup>(),
      auth = get<AuthManager>(),
      otherThings = get<OtherThingLookup>(),
    )
  }
  single<DataLogManager> {
    DataLogManagerImpl(
      scopeResolver = get<ThingScopeResolver>(),
      storeFactory = get<EntityStoreFactory>(),
      blobs = get<LocalBlobStore>(),
      filesystem = get<BlobFilesystem>(),
      scheduler = getOrNull<UploadScheduler>(),
      importer = get<DataLogImporter>(),
      cache = get<DataLogCache>(),
      identifiers = get<ThingIdentifierLookup>(),
      parsers = dataLogParsers,
    )
  }
}
