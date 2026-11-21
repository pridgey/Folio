package dev.pridgey.folio.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dalvik.system.DelegateLastClassLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest

/** What Folio knows about one managed extension. */
data class ExtensionStatus(
    val packageName: String,
    val title: String,
    val installed: Installed? = null,
    /** A newer version in the store, if any. */
    val available: IndexedExtension? = null,
    val busy: Busy? = null,
    val error: String? = null,
    val lastChecked: Long = 0,
) {
    data class Installed(val versionName: String, val versionCode: Long, val libVersion: String, val signer: String, val sources: List<ExtensionSource>)

    enum class Busy { CHECKING, DOWNLOADING, INSTALLING }

    val updateAvailable: Boolean get() = available != null && (installed == null || available.versionCode > installed.versionCode)
}

/**
 * Installs, loads and updates extensions privately (inside Folio's own storage, no system install
 * prompt). Every APK must be signed by a trusted store key, checked on install and on every load, so
 * nothing else can be slipped in. When a source starts failing, [heal] pulls the newest build at once:
 * that's how Folio keeps up when a site changes its defenses.
 */
class ExtensionManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val index: ExtensionIndex,
) {
    private val dir = File(context.filesDir, "extensions").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("extensions", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _status = MutableStateFlow(MANAGED.associateWith { ExtensionStatus(it, titleFor(it)) })
    val status: StateFlow<Map<String, ExtensionStatus>> = _status.asStateFlow()

    /** Signing keys (SHA-256 of the certificate) that extensions may be signed with. */
    val trustedKeys: Set<String> get() = prefs.getStringSet("trustedKeys", null).orEmpty() + PINNED_KEYS

    private var lastIndex: StoreIndex? = null
    private var lastIndexAt = 0L
    private val lastHeal = mutableMapOf<String, Long>()

    // ------------------------------------------------------------------ Loading

    /** Loads whatever is installed. Cheap; call at start-up. */
    suspend fun loadInstalled() = withContext(Dispatchers.IO) {
        MANAGED.forEach { pkg ->
            val file = apkFile(pkg)
            if (file.exists()) {
                runCatching { load(pkg, file) }
                    .onSuccess { installed -> setStatus(pkg) { it.copy(installed = installed, error = null) } }
                    .onFailure { e ->
                        Log.e(TAG, "Couldn't load $pkg", e)
                        setStatus(pkg) { it.copy(installed = null, error = e.message ?: e.javaClass.simpleName) }
                    }
            }
        }
        lastCheckedFromPrefs()
    }

    private fun lastCheckedFromPrefs() {
        val at = prefs.getLong("lastChecked", 0)
        _status.update { map -> map.mapValues { it.value.copy(lastChecked = at) } }
    }

    @SuppressLint("SetWorldReadable")
    private fun load(pkg: String, file: File): ExtensionStatus.Installed {
        // Android 14+ refuses to load writable code.
        if (file.canWrite()) file.setReadOnly()
        val info = archiveInfo(file) ?: error("This isn't a valid extension")
        check(info.packageName == pkg) { "Expected $pkg, found ${info.packageName}" }
        val signer = signer(info) ?: error("The extension isn't signed")
        check(signer in trustedKeys) { "The extension is signed by an unknown key ($signer)" }
        val meta = info.applicationInfo?.metaData ?: error("The extension has no metadata")
        val lib = meta.get(META_LIB)?.toString() ?: info.versionName.orEmpty().substringBeforeLast('.')
        check(lib.toDoubleOrNull() in SUPPORTED_LIBS) { "Needs extension API $lib; this Folio supports ${SUPPORTED_LIBS.joinToString()}" }

        val loader = DelegateLastClassLoader(file.absolutePath, null, context.classLoader)
        val classes = meta.getString(META_CLASS)?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.map { if (it.startsWith(".")) pkg + it else it }
            ?: error("The extension doesn't name its sources")
        val sources = classes.flatMap { name ->
            when (val obj = Class.forName(name, false, loader).getDeclaredConstructor().newInstance()) {
                is SourceFactory -> obj.createSources()
                is Source -> listOf(obj)
                else -> error("Unknown source type ${obj.javaClass.name}")
            }
        }.filterIsInstance<HttpSource>().map { ExtensionSource(it, pkg) }
        check(sources.isNotEmpty()) { "The extension has no sources" }
        prefs.edit().apply { sources.forEach { putString("sourceName.${it.id}", it.name) } }.apply()
        return ExtensionStatus.Installed(info.versionName.orEmpty(), versionCode(info), lib, signer, sources)
    }

    // ------------------------------------------------------------------ Store

    /** Checks the store for newer versions. With [install], installs anything newer straight away. */
    suspend fun checkForUpdates(install: Boolean): Result<Int> = mutex.withLock {
        MANAGED.forEach { pkg -> setStatus(pkg) { it.copy(busy = ExtensionStatus.Busy.CHECKING) } }
        val result = runCatching {
            val store = withContext(Dispatchers.IO) { index.fetch() }
            lastIndex = store
            lastIndexAt = System.currentTimeMillis()
            prefs.edit().putLong("lastChecked", lastIndexAt).apply()
            var installed = 0
            for (pkg in MANAGED) {
                val entry = store.extensions.firstOrNull { it.packageName == pkg }
                setStatus(pkg) { it.copy(available = entry, lastChecked = lastIndexAt, busy = null) }
                if (install && entry != null && needsInstall(pkg, entry)) {
                    if (installLocked(entry)) installed++
                }
            }
            installed
        }
        MANAGED.forEach { pkg -> setStatus(pkg) { it.copy(busy = null) } }
        result.onFailure { e -> MANAGED.forEach { pkg -> setStatus(pkg) { it.copy(error = it.error ?: e.message) } } }
        result
    }

    private fun needsInstall(pkg: String, entry: IndexedExtension): Boolean {
        if (entry.libVersion.toDoubleOrNull() !in SUPPORTED_LIBS) {
            setStatus(pkg) { it.copy(error = "The newest ${entry.name} needs a newer Folio (extension API ${entry.libVersion})") }
            return false
        }
        // Only MangaFire installs itself; optional sources wait until they're asked for.
        val current = _status.value[pkg]?.installed ?: return pkg == MANGAFIRE
        return entry.versionCode > current.versionCode
    }

    /** The name of a source that was installed at some point, so its titles stay labelled after removal. */
    fun rememberedName(sourceId: Long): String? = prefs.getString("sourceName.$sourceId", null)

    /** Installs one extension from the store. */
    suspend fun install(pkg: String): Result<Unit> = mutex.withLock {
        runCatching {
            val store = lastIndex?.takeIf { System.currentTimeMillis() - lastIndexAt < 60_000 } ?: index.fetch().also {
                lastIndex = it
                lastIndexAt = System.currentTimeMillis()
            }
            val entry = store.extensions.firstOrNull { it.packageName == pkg } ?: error("${titleFor(pkg)} isn't in the store anymore")
            setStatus(pkg) { it.copy(available = entry) }
            check(installLocked(entry)) { _status.value[pkg]?.error ?: "Install failed" }
        }
    }

    private suspend fun installLocked(entry: IndexedExtension): Boolean {
        val pkg = entry.packageName
        setStatus(pkg) { it.copy(busy = ExtensionStatus.Busy.DOWNLOADING, error = null) }
        return try {
            val temp = File(context.cacheDir, "$pkg.download.apk")
            withContext(Dispatchers.IO) {
                client.newCall(GET(entry.apkUrl)).awaitSuccess().use { response ->
                    temp.delete()
                    temp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                }
            }
            setStatus(pkg) { it.copy(busy = ExtensionStatus.Busy.INSTALLING) }
            val installed = withContext(Dispatchers.IO) {
                val info = archiveInfo(temp) ?: error("The download isn't a valid APK")
                check(info.packageName == pkg) { "The download is ${info.packageName}, not $pkg" }
                val signer = signer(info)
                check(signer != null && signer in trustedKeys) { "The download isn't signed by a trusted key" }
                val current = _status.value[pkg]?.installed
                check(current == null || versionCode(info) >= current.versionCode) { "Won't downgrade" }

                val target = apkFile(pkg)
                val staged = File(dir, "$pkg.staged")
                staged.delete()
                temp.copyTo(staged, overwrite = true)
                staged.setReadOnly()
                target.setWritable(true)
                check(staged.renameTo(target)) { "Couldn't replace the old version" }
                temp.delete()
                load(pkg, target)
            }
            setStatus(pkg) { it.copy(installed = installed, busy = null, error = null) }
            Log.i(TAG, "Installed $pkg ${installed.versionName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install of $pkg failed", e)
            setStatus(pkg) { it.copy(busy = null, error = e.message ?: e.javaClass.simpleName) }
            false
        }
    }

    /**
     * Called when a source keeps failing: if the store has a newer build, install it now. Returns true
     * when something changed and the call is worth retrying. Rate limited per package.
     */
    suspend fun heal(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lastHeal) {
            if (now - (lastHeal[pkg] ?: 0) < HEAL_COOLDOWN_MS) return false
            lastHeal[pkg] = now
        }
        val before = _status.value[pkg]?.installed?.versionCode
        checkForUpdates(install = true)
        val after = _status.value[pkg]?.installed?.versionCode
        return after != null && after != before
    }

    fun trustKey(fingerprint: String) {
        prefs.edit().putStringSet("trustedKeys", trustedKeys + fingerprint.lowercase()).apply()
    }

    fun uninstall(pkg: String) {
        apkFile(pkg).apply { setWritable(true); delete() }
        setStatus(pkg) { it.copy(installed = null) }
    }

    // ------------------------------------------------------------------ Helpers

    private fun setStatus(pkg: String, transform: (ExtensionStatus) -> ExtensionStatus) =
        _status.update { map -> map + (pkg to transform(map[pkg] ?: ExtensionStatus(pkg, titleFor(pkg)))) }

    private fun apkFile(pkg: String) = File(dir, "$pkg.apk")

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): PackageInfo? {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }
        info?.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }
        return info
    }

    private fun signer(info: PackageInfo): String? {
        val signing = info.signingInfo ?: return null
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        val cert = certs?.lastOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    companion object {
        private const val TAG = "FolioExtensions"
        private const val META_CLASS = "tachiyomi.extension.class"
        private const val META_LIB = "tachiyomix.extensionLib"
        private const val HEAL_COOLDOWN_MS = 5 * 60 * 1000L

        const val MANGAFIRE = "eu.kanade.tachiyomi.extension.all.mangafire"
        const val COMIX = "eu.kanade.tachiyomi.extension.en.comix"
        val MANAGED = listOf(MANGAFIRE, COMIX)
        val SUPPORTED_LIBS = setOf(1.6)

        /** Keiyoushi's signing key. */
        val PINNED_KEYS = setOf("9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2")

        fun titleFor(pkg: String) = when (pkg) {
            MANGAFIRE -> "MangaFire"
            COMIX -> "Comix"
            else -> pkg.substringAfterLast('.')
        }
    }
}
