@file:OptIn(ExperimentalSerializationApi::class)

package dev.pridgey.folio.source

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/** One extension listed in a store's index. */
data class IndexedExtension(
    val name: String,
    val packageName: String,
    val apkUrl: String,
    val iconUrl: String,
    val libVersion: String,
    val versionCode: Long,
    val versionName: String,
    val sources: List<Pair<Long, String>>,
)

data class StoreIndex(val name: String, val signingKey: String, val extensions: List<IndexedExtension>)

/**
 * Reads an extension store (Keiyoushi's by default): `repo.json` points at a gzipped protobuf index
 * that lists every extension, its APK and the key it's signed with.
 */
class ExtensionIndex(private val client: OkHttpClient, private val json: Json) {

    @Serializable
    private class RepoJson(@SerialName("index_v2") val indexV2: String? = null, val meta: Meta) {
        @Serializable
        class Meta(val name: String, val signingKeyFingerprint: String)
    }

    @Serializable
    private class PbStore(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val badgeLabel: String = "",
        @ProtoNumber(3) val signingKey: String = "",
        @ProtoNumber(101) val extensionList: PbList? = null,
    )

    @Serializable
    private class PbList(@ProtoNumber(1) val extensions: List<PbExtension> = emptyList())

    @Serializable
    private class PbExtension(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val packageName: String = "",
        @ProtoNumber(3) val resources: PbResources? = null,
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long = 0,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(8) val sources: List<PbSource> = emptyList(),
    )

    @Serializable
    private class PbResources(@ProtoNumber(1) val apkUrl: String = "", @ProtoNumber(2) val iconUrl: String = "")

    @Serializable
    private class PbSource(@ProtoNumber(1) val id: Long = 0, @ProtoNumber(2) val name: String = "", @ProtoNumber(3) val language: String = "")

    private val proto = ProtoBuf { }

    suspend fun fetch(repoUrl: String = KEIYOUSHI_REPO): StoreIndex {
        val noCache = CacheControl.Builder().noCache().build()
        val repo = client.newCall(GET(repoUrl, cache = noCache)).awaitSuccess().use { json.decodeFromString<RepoJson>(it.body.string()) }
        val indexUrl = repo.indexV2 ?: error("This store has no v2 index")
        val bytes = client.newCall(GET(indexUrl, cache = noCache)).awaitSuccess().use { it.body.bytes() }
        val raw = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else {
            bytes
        }
        val store = proto.decodeFromByteArray<PbStore>(raw)
        check(store.signingKey.equals(repo.meta.signingKeyFingerprint, ignoreCase = true)) { "The store's index and metadata disagree about its signing key" }
        return StoreIndex(
            name = store.name.ifEmpty { repo.meta.name },
            signingKey = store.signingKey.lowercase(),
            extensions = store.extensionList?.extensions.orEmpty().map { e ->
                IndexedExtension(
                    name = e.name,
                    packageName = e.packageName,
                    apkUrl = e.resources?.apkUrl.orEmpty(),
                    iconUrl = e.resources?.iconUrl.orEmpty(),
                    libVersion = e.extensionLib,
                    versionCode = e.versionCode,
                    versionName = e.versionName,
                    sources = e.sources.map { it.id to it.language },
                )
            },
        )
    }

    companion object {
        const val KEIYOUSHI_REPO = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json"
    }
}
