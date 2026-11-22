package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.IOException

private val Context.billiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "billi_app_preferences"
)

private const val STARTUP_THEME_CACHE_PREFS = "billi_startup_theme_cache"

enum class AppThemeMode(val wireValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromWire(raw: String?): AppThemeMode =
            entries.firstOrNull { it.wireValue == raw } ?: SYSTEM
    }
}

data class WidgetSnapshot(
    val tripId: String = "",
    val tripTitle: String = "No active trip",
    val balanceSubtitle: String = "Open Billi to start a trip",
    val balanceAmountText: String = "₹0.00",
    val netBalanceCents: Long = 0L,
    val pendingSyncCount: Int = 0
)

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val amoledDark: Boolean = false,
    val useSystemFont: Boolean = false,
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = false,
    val tripBubbleEnabled: Boolean = false,
    val hasSeenOnboardingWalkthrough: Boolean = false,
    val lastUpdateCheckMillis: Long = 0L,
    val cachedReleaseEtag: String = "",
    val cachedReleaseJson: String = "",
    val lastUsedSplitMode: String = "equal",
    val widgetSnapshot: WidgetSnapshot = WidgetSnapshot()
)

class AppPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.billiPreferencesDataStore
    private val startupMirror = appContext.getSharedPreferences(
        STARTUP_THEME_CACHE_PREFS,
        Context.MODE_PRIVATE
    )

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val TRIP_BUBBLE_ENABLED = booleanPreferencesKey("trip_bubble_enabled")
        val HAS_SEEN_WALKTHROUGH = booleanPreferencesKey("has_seen_walkthrough")
        val LAST_UPDATE_CHECK_MILLIS = longPreferencesKey("last_update_check_millis")
        val CACHED_RELEASE_ETAG = stringPreferencesKey("cached_release_etag")
        val CACHED_RELEASE_JSON = stringPreferencesKey("cached_release_json")
        val LAST_USED_SPLIT_MODE = stringPreferencesKey("last_used_split_mode")

        // Glance Widget & Floating Bubble shared snapshot
        val WIDGET_TRIP_ID = stringPreferencesKey("widget_trip_id")
        val WIDGET_TRIP_TITLE = stringPreferencesKey("widget_trip_title")
        val WIDGET_BALANCE_SUBTITLE = stringPreferencesKey("widget_balance_subtitle")
        val WIDGET_BALANCE_AMOUNT = stringPreferencesKey("widget_balance_amount")
        val WIDGET_NET_CENTS = longPreferencesKey("widget_net_cents")
        val WIDGET_PENDING_SYNC = intPreferencesKey("widget_pending_sync")
    }

    /**
     * Reads cached visual & feedback preferences synchronously (<1ms) so cold starts render
     * the exact saved theme, AMOLED black mode, font, and motion settings on frame 0 with zero
     * light-to-dark flash while DataStore loads asynchronously.
     */
    fun readStartupPreferencesSync(): AppPreferences {
        return AppPreferences(
            themeMode = AppThemeMode.fromWire(startupMirror.getString("theme_mode", null)),
            amoledDark = startupMirror.getBoolean("amoled_dark", false),
            useSystemFont = startupMirror.getBoolean("use_system_font", false),
            reduceMotion = startupMirror.getBoolean("reduce_motion", false),
            hapticsEnabled = startupMirror.getBoolean("haptics_enabled", true),
            soundEffectsEnabled = startupMirror.getBoolean("sound_effects_enabled", false)
        )
    }

    private fun mirrorStartupVisualPrefs(prefs: AppPreferences) {
        val existingMode = startupMirror.getString("theme_mode", null)
        val existingAmoled = startupMirror.getBoolean("amoled_dark", false)
        val existingFont = startupMirror.getBoolean("use_system_font", false)
        val existingMotion = startupMirror.getBoolean("reduce_motion", false)
        val existingHaptics = startupMirror.getBoolean("haptics_enabled", true)
        val existingSound = startupMirror.getBoolean("sound_effects_enabled", false)
        if (existingMode == prefs.themeMode.wireValue &&
            existingAmoled == prefs.amoledDark &&
            existingFont == prefs.useSystemFont &&
            existingMotion == prefs.reduceMotion &&
            existingHaptics == prefs.hapticsEnabled &&
            existingSound == prefs.soundEffectsEnabled
        ) {
            return
        }
        startupMirror.edit()
            .putString("theme_mode", prefs.themeMode.wireValue)
            .putBoolean("amoled_dark", prefs.amoledDark)
            .putBoolean("use_system_font", prefs.useSystemFont)
            .putBoolean("reduce_motion", prefs.reduceMotion)
            .putBoolean("haptics_enabled", prefs.hapticsEnabled)
            .putBoolean("sound_effects_enabled", prefs.soundEffectsEnabled)
            .apply()
    }

    val preferencesFlow: Flow<AppPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            AppPreferences(
                themeMode = AppThemeMode.fromWire(prefs[Keys.THEME_MODE]),
                amoledDark = prefs[Keys.AMOLED_DARK] ?: false,
                useSystemFont = prefs[Keys.USE_SYSTEM_FONT] ?: false,
                reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
                soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS_ENABLED] ?: false,
                tripBubbleEnabled = prefs[Keys.TRIP_BUBBLE_ENABLED] ?: false,
                hasSeenOnboardingWalkthrough = prefs[Keys.HAS_SEEN_WALKTHROUGH] ?: false,
                lastUpdateCheckMillis = prefs[Keys.LAST_UPDATE_CHECK_MILLIS] ?: 0L,
                cachedReleaseEtag = prefs[Keys.CACHED_RELEASE_ETAG].orEmpty(),
                cachedReleaseJson = prefs[Keys.CACHED_RELEASE_JSON].orEmpty(),
                lastUsedSplitMode = prefs[Keys.LAST_USED_SPLIT_MODE] ?: "equal",
                widgetSnapshot = WidgetSnapshot(
                    tripId = prefs[Keys.WIDGET_TRIP_ID].orEmpty(),
                    tripTitle = prefs[Keys.WIDGET_TRIP_TITLE] ?: "No active trip",
                    balanceSubtitle = prefs[Keys.WIDGET_BALANCE_SUBTITLE] ?: "Open Billi to start a trip",
                    balanceAmountText = prefs[Keys.WIDGET_BALANCE_AMOUNT] ?: "₹0.00",
                    netBalanceCents = prefs[Keys.WIDGET_NET_CENTS] ?: 0L,
                    pendingSyncCount = prefs[Keys.WIDGET_PENDING_SYNC] ?: 0
                )
            )
        }
        .onEach { mirrorStartupVisualPrefs(it) }

    suspend fun currentPreferences(): AppPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: AppThemeMode) {
        startupMirror.edit().putString("theme_mode", mode.wireValue).apply()
        dataStore.edit { it[Keys.THEME_MODE] = mode.wireValue }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        startupMirror.edit().putBoolean("amoled_dark", enabled).apply()
        dataStore.edit { it[Keys.AMOLED_DARK] = enabled }
    }

    suspend fun setUseSystemFont(useSystem: Boolean) {
        startupMirror.edit().putBoolean("use_system_font", useSystem).apply()
        dataStore.edit { it[Keys.USE_SYSTEM_FONT] = useSystem }
    }

    suspend fun setReduceMotion(reduce: Boolean) {
        startupMirror.edit().putBoolean("reduce_motion", reduce).apply()
        dataStore.edit { it[Keys.REDUCE_MOTION] = reduce }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        startupMirror.edit().putBoolean("haptics_enabled", enabled).apply()
        dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        startupMirror.edit().putBoolean("sound_effects_enabled", enabled).apply()
        dataStore.edit { it[Keys.SOUND_EFFECTS_ENABLED] = enabled }
    }

    suspend fun setTripBubbleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TRIP_BUBBLE_ENABLED] = enabled }
    }

    suspend fun setHasSeenOnboardingWalkthrough(seen: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_WALKTHROUGH] = seen }
    }

    suspend fun setLastUpdateCheckMillis(millis: Long) {
        dataStore.edit { it[Keys.LAST_UPDATE_CHECK_MILLIS] = millis }
    }

    suspend fun saveReleaseCache(etag: String, rawJson: String, checkedAtMillis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.CACHED_RELEASE_ETAG] = etag
            prefs[Keys.CACHED_RELEASE_JSON] = rawJson
            prefs[Keys.LAST_UPDATE_CHECK_MILLIS] = checkedAtMillis
        }
    }

    suspend fun clearReleaseCache() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.CACHED_RELEASE_ETAG)
            prefs.remove(Keys.CACHED_RELEASE_JSON)
        }
    }

    suspend fun setLastUsedSplitMode(wireValue: String) {
        dataStore.edit { it[Keys.LAST_USED_SPLIT_MODE] = wireValue }
    }

    suspend fun saveWidgetSnapshot(snapshot: WidgetSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.WIDGET_TRIP_ID] = snapshot.tripId
            prefs[Keys.WIDGET_TRIP_TITLE] = snapshot.tripTitle
            prefs[Keys.WIDGET_BALANCE_SUBTITLE] = snapshot.balanceSubtitle
            prefs[Keys.WIDGET_BALANCE_AMOUNT] = snapshot.balanceAmountText
            prefs[Keys.WIDGET_NET_CENTS] = snapshot.netBalanceCents
            prefs[Keys.WIDGET_PENDING_SYNC] = snapshot.pendingSyncCount
        }
    }
}
