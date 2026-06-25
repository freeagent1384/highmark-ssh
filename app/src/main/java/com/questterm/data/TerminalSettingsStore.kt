package com.questterm.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists user-adjustable terminal display settings.
 *
 * On a Quest the OS delivers only a single emulated pointer, so the Termux
 * pinch-to-zoom path (ScaleGestureDetector -> onScale) never fires. The font
 * size therefore has to be driven by explicit in-app controls, and its value
 * persisted here so it survives across sessions.
 */
@Singleton
class TerminalSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("questterm_terminal_settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(
        prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    )
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    /** Increase the terminal font size by one step, clamped to the allowed range. */
    fun increaseFontSize() = setFontSize(_fontSize.value + FONT_SIZE_STEP)

    /** Decrease the terminal font size by one step, clamped to the allowed range. */
    fun decreaseFontSize() = setFontSize(_fontSize.value - FONT_SIZE_STEP)

    private fun setFontSize(size: Int) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        if (clamped == _fontSize.value) return
        _fontSize.value = clamped
        prefs.edit().putInt(KEY_FONT_SIZE, clamped).apply()
    }

    companion object {
        const val MIN_FONT_SIZE = 10
        const val MAX_FONT_SIZE = 60
        const val DEFAULT_FONT_SIZE = 28
        const val FONT_SIZE_STEP = 2

        private const val KEY_FONT_SIZE = "font_size"
    }
}
