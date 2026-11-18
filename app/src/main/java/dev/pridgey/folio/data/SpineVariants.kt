package dev.pridgey.folio.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The bookcase's easter egg: a two-finger tap on a book moves it on to its next spine design, and a
 * two-finger hold puts it back. Each title's designs come in a fixed order, so this only remembers how
 * far along each title is.
 */
class SpineVariants(context: Context) {
    private val prefs = context.getSharedPreferences("spine_variants", Context.MODE_PRIVATE)
    private val _variants = MutableStateFlow(prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap())

    /** How many times each title (by [key]) has been moved on; missing means its first design. */
    val variants: StateFlow<Map<String, Int>> = _variants.asStateFlow()

    /** Back to the title's own design. */
    fun reset(key: String) {
        if (key !in _variants.value) return
        prefs.edit().remove(key).apply()
        _variants.update { it - key }
    }

    fun next(key: String) {
        val n = (_variants.value[key] ?: 0) + 1
        prefs.edit().putInt(key, n).apply()
        _variants.update { it + (key to n) }
    }
}
