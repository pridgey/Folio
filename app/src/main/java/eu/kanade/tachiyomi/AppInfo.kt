package eu.kanade.tachiyomi

import dev.pridgey.folio.BuildConfig

/** Host app version, which extensions may put in their User-Agent. */
@Suppress("Unused")
object AppInfo {
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE
    fun getVersionName(): String = BuildConfig.VERSION_NAME
}
