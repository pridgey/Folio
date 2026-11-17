package eu.kanade.tachiyomi.source.model

import android.net.Uri

/** One page of a chapter: an [imageUrl], or a [url] the source turns into one later. */
class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    var uri: Uri? = null,
)
