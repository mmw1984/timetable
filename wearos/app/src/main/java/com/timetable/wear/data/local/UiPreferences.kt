package com.timetable.wear.data.local

import android.content.Context
import android.os.UserManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ui_preferences"
)

/** Dense-layout mode values stored under [UiPreferences.KEY_DENSE_MODE]. */
object DenseLayoutMode {
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"

    fun displayText(mode: String): String = when (mode) {
        ON -> "密集"
        OFF -> "標準"
        else -> "自動"
    }

    fun next(mode: String): String = when (mode) {
        AUTO -> ON
        ON -> OFF
        else -> AUTO
    }
}

/**
 * Returns true when the dense layout should be used.
 *
 * Pure function (no Android dependencies) so the adaptive threshold is easy
 * to unit-test and tune. Screens smaller than either threshold get the dense
 * layout when the user leaves the mode on [DenseLayoutMode.AUTO].
 */
fun resolveDenseLayout(mode: String, smallestWidthDp: Int, screenHeightDp: Int): Boolean =
    when (mode) {
        DenseLayoutMode.ON -> true
        DenseLayoutMode.OFF -> false
        else -> smallestWidthDp < DENSE_AUTO_MIN_SMALLEST_WIDTH_DP ||
            screenHeightDp < DENSE_AUTO_MIN_HEIGHT_DP
    }

/** Small round screens fall back to dense automatically; tune after emulator checks. */
const val DENSE_AUTO_MIN_SMALLEST_WIDTH_DP = 210
const val DENSE_AUTO_MIN_HEIGHT_DP = 240

/**
 * UI-only preferences (layout density, period merging). Kept in a separate
 * DataStore file from the timetable snapshot so layout experiments can be
 * reverted by simply flipping a key back — no snapshot migration involved.
 *
 * Device-protected storage mirrors [TimetableCache] so tiles can read these
 * values even before first unlock.
 */
class UiPreferences(context: Context) {

    private val credentialContext = context.applicationContext
    private val deviceContext = credentialContext.createDeviceProtectedStorageContext()
    private val userManager = credentialContext.getSystemService(UserManager::class.java)

    companion object {
        private val KEY_DENSE_MODE = stringPreferencesKey("dense_layout_mode")
        private val KEY_MERGE_CONSECUTIVE = booleanPreferencesKey("merge_consecutive")
    }

    val denseMode: Flow<String> = deviceContext.uiPreferencesDataStore.data
        .map { it[KEY_DENSE_MODE] ?: DenseLayoutMode.AUTO }

    val mergeConsecutive: Flow<Boolean> = deviceContext.uiPreferencesDataStore.data
        .map { it[KEY_MERGE_CONSECUTIVE] ?: true }

    suspend fun setDenseMode(mode: String) {
        if (userManager?.isUserUnlocked == false) return
        deviceContext.uiPreferencesDataStore.edit { it[KEY_DENSE_MODE] = mode }
    }

    suspend fun setMergeConsecutive(merge: Boolean) {
        if (userManager?.isUserUnlocked == false) return
        deviceContext.uiPreferencesDataStore.edit { it[KEY_MERGE_CONSECUTIVE] = merge }
    }
}
