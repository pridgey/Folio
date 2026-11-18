package dev.pridgey.folio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent(val label: String) { PERSIMMON("Persimmon"), INDIGO("Indigo"), SAKURA("Sakura"), MATCHA("Matcha"), GRAPHITE("Graphite") }

/** How chapters are laid out in the reader. */
enum class ReaderMode(val label: String, val short: String) {
    AUTO("Automatic", "Auto"),
    RIGHT_TO_LEFT("Right to left", "RTL"),
    LEFT_TO_RIGHT("Left to right", "LTR"),
    VERTICAL("Vertical pages", "Vertical"),
    WEBTOON("Continuous scroll", "Scroll"),
    ;

    val paged get() = this == RIGHT_TO_LEFT || this == LEFT_TO_RIGHT || this == VERTICAL
}

enum class ReaderBackground(val label: String) { BLACK("Black"), GRAY("Gray"), WHITE("White"), AUTO("Auto") }

enum class PageScale(val label: String) { FIT_SCREEN("Fit screen"), FIT_WIDTH("Fit width"), ORIGINAL("Original") }

/** How a page turn looks in the paged reader. */
enum class PageEffect(val label: String) { SLIDE("Slide"), STACK("Stack"), FADE("Fade"), TURN("Turn") }

/** How hard downloads lean on a site. Folio slows down further on its own when a site pushes back. */
enum class DownloadPace(val label: String, val gapMs: Long) { FAST("Fast", 0), BALANCED("Balanced", 250), GENTLE("Gentle", 1200) }

enum class LibrarySort(val label: String) { LAST_READ("Recently read"), LAST_UPDATED("New chapters"), UNREAD("Unread"), TITLE("Title"), DATE_ADDED("Date added") }

/**
 * App settings, kept in SharedPreferences and exposed as one [StateFlow] so screens recompose on change.
 */
class Settings(context: Context) {

    data class Values(
        val theme: ThemeMode = ThemeMode.SYSTEM,
        val accent: Accent = Accent.PERSIMMON,
        val language: String = "en",
        val fallbackEnabled: Boolean = true,
        val readerMode: ReaderMode = ReaderMode.AUTO,
        val readerBackground: ReaderBackground = ReaderBackground.BLACK,
        val pageScale: PageScale = PageScale.FIT_SCREEN,
        val tapZones: Boolean = true,
        val volumeKeys: Boolean = false,
        val keepScreenOn: Boolean = true,
        val webtoonGap: Boolean = false,
        val showPageNumber: Boolean = true,
        val nightFilter: Float = 0f,
        /** When on, the night filter only shows between [nightFrom] and [nightTo] (minutes after midnight). */
        val nightSchedule: Boolean = false,
        val nightFrom: Int = 21 * 60,
        val nightTo: Int = 7 * 60,
        val pageEffect: PageEffect = PageEffect.STACK,
        val chapterHaptics: Boolean = true,
        /** Two pages side by side when the window is wide (tablets, landscape). */
        val spreads: Boolean = true,
        val autoCrop: Boolean = false,
        /** Keep this many unread chapters downloaded ahead of where you are; 0 is off. */
        val downloadAhead: Int = 0,
        val downloadPace: DownloadPace = DownloadPace.BALANCED,
        /** Ask for the fingerprint or device PIN when Folio opens. */
        val appLock: Boolean = false,
        /** How long Folio can be in the background before it locks again, in seconds. */
        val lockAfterSeconds: Int = 60,
        val updateIntervalHours: Int = 12,
        val updateOnlyOnWifi: Boolean = true,
        val notifyNewChapters: Boolean = true,
        val autoDownload: Boolean = false,
        val deleteAfterRead: Boolean = false,
        val librarySort: LibrarySort = LibrarySort.LAST_READ,
        val libraryColumns: Int = 3,
        val onboarded: Boolean = false,
        val lastUpdatesSeen: Long = 0,
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("folio", Context.MODE_PRIVATE)
    private val _values = MutableStateFlow(read())
    val values: StateFlow<Values> = _values.asStateFlow()
    val current: Values get() = _values.value

    fun update(transform: (Values) -> Values) {
        val next = transform(_values.value)
        _values.value = next
        write(next)
    }

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
        getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun read(): Values = with(prefs) {
        val d = Values()
        Values(
            theme = enum("theme", d.theme),
            accent = enum("accent", d.accent),
            language = getString("language", d.language) ?: d.language,
            fallbackEnabled = getBoolean("fallbackEnabled", d.fallbackEnabled),
            readerMode = enum("readerMode", d.readerMode),
            readerBackground = enum("readerBackground", d.readerBackground),
            pageScale = enum("pageScale", d.pageScale),
            tapZones = getBoolean("tapZones", d.tapZones),
            volumeKeys = getBoolean("volumeKeys", d.volumeKeys),
            keepScreenOn = getBoolean("keepScreenOn", d.keepScreenOn),
            webtoonGap = getBoolean("webtoonGap", d.webtoonGap),
            showPageNumber = getBoolean("showPageNumber", d.showPageNumber),
            nightFilter = getFloat("nightFilter", d.nightFilter),
            nightSchedule = getBoolean("nightSchedule", d.nightSchedule),
            nightFrom = getInt("nightFrom", d.nightFrom),
            nightTo = getInt("nightTo", d.nightTo),
            pageEffect = enum("pageEffect", d.pageEffect),
            chapterHaptics = getBoolean("chapterHaptics", d.chapterHaptics),
            spreads = getBoolean("spreads", d.spreads),
            autoCrop = getBoolean("autoCrop", d.autoCrop),
            downloadAhead = getInt("downloadAhead", d.downloadAhead),
            downloadPace = enum("downloadPace", d.downloadPace),
            appLock = getBoolean("appLock", d.appLock),
            lockAfterSeconds = getInt("lockAfterSeconds", d.lockAfterSeconds),
            updateIntervalHours = getInt("updateIntervalHours", d.updateIntervalHours),
            updateOnlyOnWifi = getBoolean("updateOnlyOnWifi", d.updateOnlyOnWifi),
            notifyNewChapters = getBoolean("notifyNewChapters", d.notifyNewChapters),
            autoDownload = getBoolean("autoDownload", d.autoDownload),
            deleteAfterRead = getBoolean("deleteAfterRead", d.deleteAfterRead),
            librarySort = enum("librarySort", d.librarySort),
            libraryColumns = getInt("libraryColumns", d.libraryColumns),
            onboarded = getBoolean("onboarded", d.onboarded),
            lastUpdatesSeen = getLong("lastUpdatesSeen", d.lastUpdatesSeen),
        )
    }

    private fun write(v: Values) = prefs.edit()
        .putString("theme", v.theme.name)
        .putString("accent", v.accent.name)
        .putString("language", v.language)
        .putBoolean("fallbackEnabled", v.fallbackEnabled)
        .putString("readerMode", v.readerMode.name)
        .putString("readerBackground", v.readerBackground.name)
        .putString("pageScale", v.pageScale.name)
        .putBoolean("tapZones", v.tapZones)
        .putBoolean("volumeKeys", v.volumeKeys)
        .putBoolean("keepScreenOn", v.keepScreenOn)
        .putBoolean("webtoonGap", v.webtoonGap)
        .putBoolean("showPageNumber", v.showPageNumber)
        .putFloat("nightFilter", v.nightFilter)
        .putBoolean("nightSchedule", v.nightSchedule)
        .putInt("nightFrom", v.nightFrom)
        .putInt("nightTo", v.nightTo)
        .putString("pageEffect", v.pageEffect.name)
        .putBoolean("chapterHaptics", v.chapterHaptics)
        .putBoolean("spreads", v.spreads)
        .putBoolean("autoCrop", v.autoCrop)
        .putInt("downloadAhead", v.downloadAhead)
        .putString("downloadPace", v.downloadPace.name)
        .putBoolean("appLock", v.appLock)
        .putInt("lockAfterSeconds", v.lockAfterSeconds)
        .putInt("updateIntervalHours", v.updateIntervalHours)
        .putBoolean("updateOnlyOnWifi", v.updateOnlyOnWifi)
        .putBoolean("notifyNewChapters", v.notifyNewChapters)
        .putBoolean("autoDownload", v.autoDownload)
        .putBoolean("deleteAfterRead", v.deleteAfterRead)
        .putString("librarySort", v.librarySort.name)
        .putInt("libraryColumns", v.libraryColumns)
        .putBoolean("onboarded", v.onboarded)
        .putLong("lastUpdatesSeen", v.lastUpdatesSeen)
        .apply()
}
