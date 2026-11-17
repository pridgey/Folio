package eu.kanade.tachiyomi.source

import androidx.preference.PreferenceScreen

/** A source with settings. Folio renders the preferences it adds natively. */
interface ConfigurableSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
