package dev.pridgey.folio

import android.app.Application
import dev.pridgey.folio.data.FolioDatabase
import dev.pridgey.folio.data.LibraryRepository
import dev.pridgey.folio.data.Settings
import dev.pridgey.folio.download.Downloads
import dev.pridgey.folio.source.ExtensionIndex
import dev.pridgey.folio.source.ExtensionManager
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.SourceManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.fullType

/** The app's long-lived objects. Built once in [FolioApp.onCreate]. */
object Graph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    lateinit var network: NetworkHelper
        private set
    lateinit var settings: Settings
        private set
    lateinit var db: FolioDatabase
        private set
    lateinit var extensions: ExtensionManager
        private set
    lateinit var sources: SourceManager
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var downloads: Downloads
        private set

    @OptIn(ExperimentalSerializationApi::class)
    fun init(app: Application) {
        this.app = app
        network = NetworkHelper(app)

        // Extensions look these up through Injekt by their exact types.
        Injekt.addSingleton(fullType<Application>(), app)
        Injekt.addSingleton(fullType<NetworkHelper>(), network)
        Injekt.addSingleton(fullType<Json>(), json)
        Injekt.addSingleton(fullType<ProtoBuf>(), ProtoBuf)

        settings = Settings(app)
        db = FolioDatabase.create(app)
        extensions = ExtensionManager(app, network.client, ExtensionIndex(network.client, json))
        sources = SourceManager(extensions, MangaDexSource(network.client, json) { settings.current.language }, settings)
        library = LibraryRepository(db, sources, json)
        downloads = Downloads(app, db, sources, library)
    }
}
