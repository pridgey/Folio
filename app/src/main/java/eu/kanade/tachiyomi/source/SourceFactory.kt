package eu.kanade.tachiyomi.source

/** Creates several sources from one extension (older extensions). */
interface SourceFactory {
    fun createSources(): List<Source>
}
