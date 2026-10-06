package com.timetable.wear.data.local

import android.content.Context
import android.os.UserManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timetable.wear.data.model.TimetableSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.timetableDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "timetable_cache"
)

@Serializable
data class CachedTimetableSnapshot(
    val urlBase: String,
    val dayRotation: Map<String, Int>,
    val specialDates: Map<String, String>,
    val subjectSchedule: Map<String, Map<String, String>>,
    val timetables: Map<String, TimetableSchedule>
) {
    fun subjectScheduleAsInts(): Map<Int, Map<Int, String>> = subjectSchedule.mapNotNull { (day, periods) ->
        day.toIntOrNull()?.let { parsedDay ->
            parsedDay to periods.mapNotNull { (period, subject) ->
                period.toIntOrNull()?.let { parsedPeriod -> parsedPeriod to subject }
            }.toMap()
        }
    }.toMap()

    fun isValid(): Boolean =
        dayRotation.isNotEmpty() &&
            subjectSchedule.values.any { it.isNotEmpty() } &&
            timetables["normal"]?.periods?.isNotEmpty() == true

    companion object {
        fun from(
            urlBase: String,
            dayRotation: Map<String, Int>,
            specialDates: Map<String, String>,
            subjectSchedule: Map<Int, Map<Int, String>>,
            timetables: Map<String, TimetableSchedule>
        ): CachedTimetableSnapshot = CachedTimetableSnapshot(
            urlBase = urlBase,
            dayRotation = dayRotation,
            specialDates = specialDates,
            subjectSchedule = subjectSchedule.mapKeys { it.key.toString() }.mapValues { (_, periods) ->
                periods.mapKeys { it.key.toString() }
            },
            timetables = timetables
        )
    }
}

interface TimetableCacheStore {
    suspend fun loadSnapshot(): CachedTimetableSnapshot?
    suspend fun saveSnapshot(snapshot: CachedTimetableSnapshot)
}

class TimetableCache(context: Context) : TimetableCacheStore {

    private val credentialContext = context.applicationContext
    private val deviceContext = credentialContext.createDeviceProtectedStorageContext()
    private val userManager = credentialContext.getSystemService(UserManager::class.java)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    companion object {
        private val KEY_SNAPSHOT = stringPreferencesKey("snapshot_v2")

        // Legacy keys are read once after an upgrade, then replaced by KEY_SNAPSHOT.
        private val KEY_DAY_ROTATION = stringPreferencesKey("day_rotation")
        private val KEY_SPECIAL_DATES = stringPreferencesKey("special_dates")
        private val KEY_TIMETABLE = stringPreferencesKey("timetable")
        private val KEY_SUBJECT_SCHEDULE = stringPreferencesKey("subject_schedule")
        private val KEY_URL_BASE = stringPreferencesKey("url_base")
    }

    override suspend fun loadSnapshot(): CachedTimetableSnapshot? = withContext(Dispatchers.IO) {
        readDeviceSnapshot()?.let { return@withContext it }
        migrateLegacySnapshotIfPossible()
        readDeviceSnapshot()
    }

    override suspend fun saveSnapshot(snapshot: CachedTimetableSnapshot): Unit = withContext(Dispatchers.IO) {
        require(snapshot.isValid()) { "Cannot persist an incomplete timetable snapshot." }
        val serialized = json.encodeToString(snapshot)
        deviceContext.timetableDataStore.edit { preferences ->
            preferences[KEY_SNAPSHOT] = serialized
        }
        Unit
    }

    private suspend fun readDeviceSnapshot(): CachedTimetableSnapshot? = try {
        val raw = deviceContext.timetableDataStore.data.first()[KEY_SNAPSHOT] ?: return null
        json.decodeFromString<CachedTimetableSnapshot>(raw).takeIf { it.isValid() }
    } catch (_: Exception) {
        null
    }

    private suspend fun migrateLegacySnapshotIfPossible() {
        if (userManager?.isUserUnlocked != true) return

        val legacy = try {
            credentialContext.timetableDataStore.data.first()
        } catch (_: Exception) {
            return
        }

        legacy[KEY_SNAPSHOT]?.let { raw ->
            runCatching { json.decodeFromString<CachedTimetableSnapshot>(raw) }
                .getOrNull()
                ?.takeIf { it.isValid() }
                ?.let {
                    saveSnapshot(it)
                    return
                }
        }

        val dayRotation = legacy[KEY_DAY_ROTATION]?.let {
            runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull()
        } ?: return
        val specialDates = legacy[KEY_SPECIAL_DATES]?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        } ?: return
        val timetables = legacy[KEY_TIMETABLE]?.let {
            runCatching { json.decodeFromString<Map<String, TimetableSchedule>>(it) }.getOrNull()
        } ?: return
        val subjects = legacy[KEY_SUBJECT_SCHEDULE]?.let {
            runCatching {
                json.decodeFromString<Map<String, Map<String, String>>>(it)
                    .mapNotNull { (day, periods) ->
                        day.toIntOrNull()?.let { parsedDay ->
                            parsedDay to periods.mapNotNull { (period, subject) ->
                                period.toIntOrNull()?.let { parsedPeriod -> parsedPeriod to subject }
                            }.toMap()
                        }
                    }
                    .toMap()
            }.getOrNull()
        } ?: return

        val snapshot = CachedTimetableSnapshot.from(
            urlBase = legacy[KEY_URL_BASE].orEmpty(),
            dayRotation = dayRotation,
            specialDates = specialDates,
            subjectSchedule = subjects,
            timetables = timetables
        )
        if (snapshot.isValid()) saveSnapshot(snapshot)
    }
}
