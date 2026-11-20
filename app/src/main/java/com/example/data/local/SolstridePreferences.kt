package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.SolstrideDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.flow.first

private val Context.solstrideDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "solstride_preferences"
)

data class SolstrideSettings(
    val displayMode: SolstrideDisplayMode = SolstrideDisplayMode.SUNLIT_PARCHMENT,
    val hapticsEnabled: Boolean = true,
    val batteryBatchingEnabled: Boolean = true,
    val lastHardwareStepCount: Long = -1L,
    val lastWatchSyncMs: Long = 0L,
    val watchOnlyWarningAcknowledged: Boolean = true,
    val pedometerEnabled: Boolean = true,
    val pedometerDailySteps: Long = 0L,
    val pedometerDateKey: String = "",
    val dailyStepGoal: Long = 6000L,
    val healthConnectAllSources: Boolean = false
)

class SolstridePreferences(private val context: Context) {
    private object Keys {
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val BATTERY_BATCHING = booleanPreferencesKey("battery_batching")
        val LAST_HW_STEP_COUNT = longPreferencesKey("last_hw_step_count")
        val LAST_WATCH_SYNC_MS = longPreferencesKey("last_watch_sync_ms")
        val WATCH_ONLY_WARNING_ACKNOWLEDGED = booleanPreferencesKey("watch_only_warning_acknowledged")
        val PEDOMETER_ENABLED = booleanPreferencesKey("pedometer_enabled")
        val PEDOMETER_DAILY_STEPS = longPreferencesKey("pedometer_daily_steps")
        val PEDOMETER_DATE_KEY = stringPreferencesKey("pedometer_date_key")
        val DAILY_STEP_GOAL = longPreferencesKey("daily_step_goal")
        val HEALTH_CONNECT_WATERMARKS = stringPreferencesKey("hc_step_watermarks_v1")
        val HEALTH_CONNECT_ALL_SOURCES = booleanPreferencesKey("hc_all_sources")
        val LAST_WATCH_STEP_RECEIVED_MS = longPreferencesKey("last_watch_step_received_ms")
    }

    val settingsFlow: Flow<SolstrideSettings> = context.solstrideDataStore.data.map { prefs ->
        val mode = when (prefs[Keys.DISPLAY_MODE]) {
            SolstrideDisplayMode.OBSIDIAN_OLED.name -> SolstrideDisplayMode.OBSIDIAN_OLED
            else -> SolstrideDisplayMode.SUNLIT_PARCHMENT
        }
        SolstrideSettings(
            displayMode = mode,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            batteryBatchingEnabled = prefs[Keys.BATTERY_BATCHING] ?: true,
            lastHardwareStepCount = prefs[Keys.LAST_HW_STEP_COUNT] ?: -1L,
            lastWatchSyncMs = prefs[Keys.LAST_WATCH_SYNC_MS] ?: 0L,
            watchOnlyWarningAcknowledged = prefs[Keys.WATCH_ONLY_WARNING_ACKNOWLEDGED] ?: true,
            pedometerEnabled = prefs[Keys.PEDOMETER_ENABLED] ?: true,
            pedometerDailySteps = prefs[Keys.PEDOMETER_DAILY_STEPS] ?: 0L,
            pedometerDateKey = prefs[Keys.PEDOMETER_DATE_KEY] ?: "",
            dailyStepGoal = (prefs[Keys.DAILY_STEP_GOAL] ?: 6000L).coerceIn(2000L, 25000L),
            healthConnectAllSources = prefs[Keys.HEALTH_CONNECT_ALL_SOURCES] ?: false
        )
    }

    suspend fun setDisplayMode(mode: SolstrideDisplayMode) {
        context.solstrideDataStore.edit { it[Keys.DISPLAY_MODE] = mode.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.solstrideDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setBatteryBatchingEnabled(enabled: Boolean) {
        context.solstrideDataStore.edit { it[Keys.BATTERY_BATCHING] = enabled }
    }

    suspend fun setLastHardwareStepCount(count: Long) {
        context.solstrideDataStore.edit { it[Keys.LAST_HW_STEP_COUNT] = count }
    }

    suspend fun setLastWatchSyncMs(timestampMs: Long) {
        context.solstrideDataStore.edit { it[Keys.LAST_WATCH_SYNC_MS] = timestampMs }
    }

    suspend fun acknowledgeWatchOnlyWarning() {
        context.solstrideDataStore.edit { it[Keys.WATCH_ONLY_WARNING_ACKNOWLEDGED] = true }
    }

    suspend fun setPedometerEnabled(enabled: Boolean) {
        context.solstrideDataStore.edit { it[Keys.PEDOMETER_ENABLED] = enabled }
    }

    suspend fun recordDailyPedometerSteps(dateKey: String, delta: Long) {
        if (delta <= 0L) return
        context.solstrideDataStore.edit { prefs ->
            val currentKey = prefs[Keys.PEDOMETER_DATE_KEY] ?: ""
            val currentCount = if (currentKey == dateKey) prefs[Keys.PEDOMETER_DAILY_STEPS] ?: 0L else 0L
            prefs[Keys.PEDOMETER_DATE_KEY] = dateKey
            prefs[Keys.PEDOMETER_DAILY_STEPS] = (currentCount + delta).coerceAtMost(500_000L)
        }
    }

    suspend fun setDailyStepGoal(goal: Long) {
        context.solstrideDataStore.edit { it[Keys.DAILY_STEP_GOAL] = goal.coerceIn(2000L, 25000L) }
    }

    suspend fun readLastHardwareStepCount(): Long {
        return context.solstrideDataStore.data.first()[Keys.LAST_HW_STEP_COUNT] ?: -1L
    }

    suspend fun readPedometerEnabled(): Boolean {
        return context.solstrideDataStore.data.first()[Keys.PEDOMETER_ENABLED] ?: true
    }

    suspend fun readHealthConnectAllSources(): Boolean {
        return context.solstrideDataStore.data.first()[Keys.HEALTH_CONNECT_ALL_SOURCES] ?: false
    }

    suspend fun setHealthConnectAllSources(allSources: Boolean) {
        context.solstrideDataStore.edit { it[Keys.HEALTH_CONNECT_ALL_SOURCES] = allSources }
    }

    suspend fun readLastWatchStepReceivedMs(): Long {
        return context.solstrideDataStore.data.first()[Keys.LAST_WATCH_STEP_RECEIVED_MS] ?: 0L
    }

    suspend fun setLastWatchStepReceivedMs(timestampMs: Long) {
        context.solstrideDataStore.edit { it[Keys.LAST_WATCH_STEP_RECEIVED_MS] = timestampMs }
    }

    suspend fun readHealthConnectWatermarksRaw(): String {
        return context.solstrideDataStore.data.first()[Keys.HEALTH_CONNECT_WATERMARKS] ?: ""
    }

    suspend fun writeHealthConnectWatermarksRaw(encoded: String) {
        context.solstrideDataStore.edit { it[Keys.HEALTH_CONNECT_WATERMARKS] = encoded }
    }
}
