package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SManga

interface CatalogueSource : Source {
    /** An ISO 639-1 language code. */
    val lang: String

    val supportsRelatedMangas: Boolean get() = false
    val disableRelatedMangasBySearch: Boolean get() = false
    val disableRelatedMangas: Boolean get() = false

    suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException("Unsupported!")
}
