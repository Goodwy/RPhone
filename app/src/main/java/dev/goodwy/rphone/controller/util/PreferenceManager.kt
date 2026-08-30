package dev.goodwy.rphone.controller.util

import android.content.Context

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private var _sharedDataStore: DataStore<Preferences>? = null
private val dataStoreLock = Any()

private fun getSharedDataStore(context: Context): DataStore<Preferences> {
    return _sharedDataStore ?: synchronized(dataStoreLock) {
        _sharedDataStore ?: run {
            val appContext = context.applicationContext
            val deviceContext = appContext.createDeviceProtectedStorageContext()
            try {
                deviceContext.moveSharedPreferencesFrom(appContext, "rill_prefs")
            } catch (_: Exception) {}

            PreferenceDataStoreFactory.create(
                migrations = listOf(
                    SharedPreferencesMigration(
                        context = deviceContext,
                        sharedPreferencesName = "rill_prefs"
                    )
                ),
                produceFile = { deviceContext.preferencesDataStoreFile("rill_prefs") },
                scope = dataStoreScope
            )
        }.also { _sharedDataStore = it }
    }
}

class PreferenceManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore = getSharedDataStore(appContext)

    // Internal cache for synchronous access
    private val _prefsCache = MutableStateFlow<Preferences>(emptyPreferences())

    private val _settingsChanged = MutableStateFlow(0)
    val settingsChanged: StateFlow<Int> = _settingsChanged.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { newPrefs ->
                _prefsCache.value = newPrefs
                _settingsChanged.value += 1
            }
        }
    }

    /** Number of currently active SIM subscriptions (0, 1, 2+). Never throws. */
    fun getActiveSimCount(): Int {
        return try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    appContext, android.Manifest.permission.READ_PHONE_STATE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return 1
            val sm = appContext.getSystemService(android.telephony.SubscriptionManager::class.java)
            val list =
                sm?.activeSubscriptionInfoList
            list?.size ?: 1
        } catch (_: Exception) { 1 }
    }

    /** "Show SIM badges in call logs" should only default to on for dual-SIM (or more)
     *  devices — on a single-SIM device the badge is pure clutter since every entry
     *  would show the same "SIM 1" chip. */
    fun getShowSimsInCallLogsDefault(): Boolean = getActiveSimCount() >= 2

    /** Default-SIM-for-calling behavior should only default to "always ask" on
     *  dual-SIM (or more) devices. On a single-SIM device there is nothing to ask —
     *  default straight to that one SIM so every call just goes out immediately. */
    fun getDefaultSimAskEveryTimeDefault(): Boolean = getActiveSimCount() >= 2

    /** Default value for the "default_sim" pref (0 = ask every time, 1 = SIM 1, 2 = SIM 2).
     *  On a single-SIM device there's nothing to ask about, so default straight to that
     *  one SIM instead of nagging with an "Ask every time" dialog on every call. */
    fun getDefaultSimIndexDefault(): Int = if (getActiveSimCount() == 1) 1 else 0

    // Synchronous-style API for backward compatibility
    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        _prefsCache.value[booleanPreferencesKey(key)] ?: defaultValue

    fun setBoolean(key: String, value: Boolean) {
        scope.launch { dataStore.edit { it[booleanPreferencesKey(key)] = value } }
    }

    fun getString(key: String, defaultValue: String?): String? =
        _prefsCache.value[stringPreferencesKey(key)] ?: defaultValue

    fun setString(key: String, value: String?) {
        scope.launch {
            dataStore.edit {
                if (value == null) it.remove(stringPreferencesKey(key))
                else it[stringPreferencesKey(key)] = value
            }
        }
    }

    fun getInt(key: String, defaultValue: Int): Int =
        _prefsCache.value[intPreferencesKey(key)] ?: defaultValue

    fun setInt(key: String, value: Int) {
        scope.launch { dataStore.edit { it[intPreferencesKey(key)] = value } }
    }

    fun getFloat(key: String, defaultValue: Float): Float =
        _prefsCache.value[floatPreferencesKey(key)] ?: defaultValue

    fun setFloat(key: String, value: Float) {
        scope.launch { dataStore.edit { it[floatPreferencesKey(key)] = value } }
    }

    /** Returns a map of all preferences in the DataStore for backup purposes. */
    fun getAllPreferences(): Map<String, Any> {
        return _prefsCache.value.asMap().mapKeys { it.key.name }.mapValues { it.value }
    }

    /** Restores preferences from a map, typically from a backup. */
    fun restoreAllPreferences(preferences: Map<String, Any>) {
        scope.launch {
            dataStore.edit { prefs ->
                preferences.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> prefs[booleanPreferencesKey(key)] = value
                        is Int -> prefs[intPreferencesKey(key)] = value
                        is Long -> prefs[longPreferencesKey(key)] = value
                        is Float -> prefs[floatPreferencesKey(key)] = value
                        is String -> prefs[stringPreferencesKey(key)] = value
                        is Double -> prefs[floatPreferencesKey(key)] = value.toFloat()
                        // Handle comma-separated strings that represent Sets (legacy or current backup format)
                        // If it's a known set key, we might want to handle it specifically, 
                        // but usually the app reads them as CSV anyway.
                    }
                }
            }
        }
    }

    /** Returns true if an incoming call from [phoneNumber] should be gated behind biometric. */
    fun shouldGateCallWithBiometric(phoneNumber: String?): Boolean {
        if (!getBoolean(KEY_BIOMETRICS_CALL_LOCK, false)) return false
        if ((getString(KEY_BIOMETRICS_TYPE, "") ?: "").isEmpty()) return false
        val mode = getString(KEY_BIOMETRICS_CALL_LOCK_MODE, "all") ?: "all"
        if (mode == "all") return true
        if (phoneNumber.isNullOrBlank()) return mode == "skip_specified"
        val stored = getString(KEY_BIOMETRICS_CALL_LOCK_NUMBERS, "") ?: ""
        if (stored.isBlank()) return mode == "skip_specified"
        val incoming = phoneNumber.filter { it.isDigit() }.takeLast(10)
        val match = stored.split(",").any { raw ->
            val n = raw.trim().filter { it.isDigit() }.takeLast(10)
            n.isNotEmpty() && (incoming.endsWith(n) || n.endsWith(incoming))
        }
        return if (mode == "specified") match else !match
    }

    fun setLastUsedNumber(contactId: String, number: String) {
        setString("last_used_number_$contactId", number)
    }

    fun getLastUsedNumber(contactId: String): String? {
        return getString("last_used_number_$contactId", null)
    }

    fun setFavoriteNumber(contactId: String, number: String?) {
        setString("favorite_number_$contactId", number)
    }

    fun getFavoriteNumber(contactId: String): String? {
        return getString("favorite_number_$contactId", null)
    }

    fun setFavoriteSim(contactId: String, simHandle: String?) {
        setString("favorite_sim_$contactId", simHandle)
    }

    fun getFavoriteSim(contactId: String): String? {
        return getString("favorite_sim_$contactId", null)
    }

    fun setFavoriteEmail(contactId: String, email: String?) {
        setString("favorite_email_$contactId", email)
    }

    fun getFavoriteEmail(contactId: String): String? {
        return getString("favorite_email_$contactId", null)
    }

    fun getFavoritesOrder(): List<String> {
        val orderStr = getString(KEY_FAVORITES_ORDER, null) ?: return emptyList()
        return orderStr.split(",").filter { it.isNotEmpty() }
    }

    fun setFavoritesOrder(order: List<String>) {
        setString(KEY_FAVORITES_ORDER, order.joinToString(","))
    }

    fun contactBackgroundIdKey(contactId: String): String {
        return CONTACT_BACKGROUND_PREFIX + contactId
    }

    fun contactBackgroundNumberKey(numberKey: String): String {
        return CONTACT_BACKGROUND_NUMBER_PREFIX + numberKey
    }

    fun setContactBackground(contactId: String, uri: String?) {
        setString(contactBackgroundIdKey(contactId), uri)
    }

    fun getContactBackground(contactId: String): String? {
        return getString(contactBackgroundIdKey(contactId), null)
    }

    fun setContactBackgroundForNumber(numberKey: String, value: String?) {
        setString(contactBackgroundNumberKey(numberKey), value)
    }

    fun getContactBackgroundForNumber(numberKey: String): String? {
        return try {
            getString(contactBackgroundNumberKey(numberKey), null)
        } catch (e: Exception) {
            null
        }
    }

    fun getContactBackgroundEntries(): Map<String, String> {
        return try {
            _prefsCache.value.asMap()
                .filterKeys { it.name.startsWith(CONTACT_BACKGROUND_PREFIX) }
                .mapNotNull { (key, value) ->
                    val stringValue = value as? String
                    if (stringValue.isNullOrBlank()) null else key.name to stringValue
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getContactBackgroundNumberEntries(): Map<String, String> {
        return try {
            _prefsCache.value.asMap()
                .filterKeys { it.name.startsWith(CONTACT_BACKGROUND_NUMBER_PREFIX) }
                .mapNotNull { (key, value) ->
                    val stringValue = value as? String
                    if (stringValue.isNullOrBlank()) null else {
                        val numberKey = key.name.removePrefix(CONTACT_BACKGROUND_NUMBER_PREFIX)
                        numberKey to stringValue
                    }
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun updateContactBackgroundEntries(updates: Map<String, String?>) {
        if (updates.isEmpty()) return
        scope.launch {
            dataStore.edit { prefs ->
                updates.forEach { (key, value) ->
                    if (key.startsWith(CONTACT_BACKGROUND_PREFIX)) {
                        val dataStoreKey = stringPreferencesKey(key)
                        if (value == null) prefs.remove(dataStoreKey)
                        else prefs[dataStoreKey] = value
                    }
                }
            }
        }
    }

    suspend fun updateContactBackgroundEntriesSuspend(updates: Map<String, String?>) {
        if (updates.isEmpty()) return
        dataStore.edit { prefs ->
            updates.forEach { (key, value) ->
                if (key.startsWith(CONTACT_BACKGROUND_PREFIX)) {
                    val dataStoreKey = stringPreferencesKey(key)
                    if (value == null) prefs.remove(dataStoreKey)
                    else prefs[dataStoreKey] = value
                }
            }
        }
    }

    fun getVisibleAccounts(): Set<String>? {
        val str = getString(KEY_VISIBLE_ACCOUNTS, null) ?: return null
        return str.split(",").filter { it.isNotEmpty() }.toSet()
    }

    fun setVisibleAccounts(accounts: Set<String>) {
        setString(KEY_VISIBLE_ACCOUNTS, accounts.joinToString(","))
    }

    companion object {
        const val CONTACT_BACKGROUND_PREFIX = "contact_background_"
        const val CONTACT_BACKGROUND_NUMBER_PREFIX = "contact_background_num_"

        const val KEY_DEFAULT_SIM           = "default_sim"
        const val KEY_DYNAMIC_COLORS        = "dynamic_colors"
        const val KEY_AMOLED_MODE           = "amoled_mode"
        const val KEY_SHOW_FIRST_LETTER     = "show_first_letter"
        const val KEY_COLORFUL_AVATARS      = "colorful_avatars"
        const val KEY_PRIMARY_COLOR_AVATARS = "primary_color"
        const val KEY_SECONDARY_COLOR_AVATARS = "secondary_color"
        const val KEY_GOOGLE_CONTACTS_AVATARS      = "google_contacts_color"
        const val KEY_SHOW_PICTURE          = "show_picture"
        const val KEY_ICON_ONLY_NAV         = "icon_only_nav"
        const val KEY_FLIP_BOTTOM_NAV = "flip_bottom_nav"
        const val KEY_DEFAULT_BOTTOM_NAV = "default_bottom_nav"
        const val KEY_DTMF_TONE             = "dtmf_tone"
        const val KEY_DIALPAD_VIBRATION     = "dialpad_vibration"
        const val KEY_SPEED_DIAL            = "speed_dial"
        const val KEY_T9_DIALING            = "t9_dialing"
        const val KEY_PROXIMITY_SENSOR = "proximity_sensor"
        const val KEY_INCOMING_CALL_POPUP = "incoming_call_popup"
        const val KEY_AUTO_REDIAL_BUSY = "auto_redial_busy"
        const val KEY_REDIAL_ATTEMPTS = "redial_attempts"
        const val KEY_REDIAL_DELAY = "redial_delay"
        const val KEY_BLOCK_METHOD = "block_method"
        const val KEY_BLOCK_LOG_VISIBILITY = "block_log_visibility"
        const val KEY_BLOCK_NOTIFICATION = "block_notification"
        const val KEY_VIBRATE_ON_ANSWER = "vibrate_on_answer"
        const val KEY_VIBRATE_ON_HANGUP = "vibrate_on_hangup"
        const val KEY_ROUND_AVATARS = "round_avatars"
        const val KEY_SHOW_DIVIDERS = "show_dividers"
        const val KEY_TRANSITION_STYLE = "transition_animation_style"
        const val KEY_DIALPAD_STYLE = "dialpad_style"
        const val KEY_VOICEMAIL_NUMBER = "voicemail_number"
        const val KEY_VOICEMAIL_VIBRATION = "voicemail_vibration"
        const val KEY_VOICEMAIL_RINGTONE = "voicemail_ringtone"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_AUTO_ANSWER_DELAY = "auto_answer_delay"
        const val KEY_SHOW_CALL_SUMMARY_TOAST = "show_call_summary_toast"
        const val KEY_VIBRATE_OUTGOING_RINGING = "vibrate_outgoing_ringing"
        const val KEY_FLIP_TO_SILENCE = "flip_to_silence"
        const val KEY_SILENCE_UNKNOWN = "silence_unknown_calls"
        const val KEY_DISPLAY_CARRIER_INFO = "display_carrier_info"
        const val KEY_CALL_DURATION_DISPLAY = "call_duration_display_mode"
        const val KEY_CALL_LOG_GROUPING = "call_log_grouping"
        const val KEY_DIALPAD_LAYOUT = "dialpad_layout_style"
        const val KEY_AVATAR_SHAPE = "avatar_shape"
        const val KEY_SWIPE_TO_CALL = "swipe_to_call"
        const val KEY_DIALPAD_VIBRATION_STRENGTH = "dialpad_vibration_strength"
        const val KEY_DTMF_TONE_VOLUME = "dtmf_tone_volume"
        const val KEY_HAPTIC_LIST_SCROLL = "haptic_list_scroll"
        const val KEY_SHOW_SIM_ICON_HISTORY = "show_sim_icon_history"
        const val KEY_SEARCH_MATCH_MODE = "search_match_mode"
        const val KEY_QUICK_RESPONSE_ENABLED = "quick_response_enabled"
        const val KEY_INCOMING_CALL_UI_MODE = "incoming_call_ui_mode"
        const val KEY_SHOW_CARDS = "show_cards"
        const val KEY_SHOW_CALL_SCREEN_AVATAR = "show_call_screen_avatar"
        const val KEY_CARD_ROUNDNESS = "card_roundness"
        const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        const val KEY_LAST_USED_ACCOUNT_NAME = "last_used_account_name"
        const val KEY_LAST_USED_ACCOUNT_TYPE = "last_used_account_type"
        const val KEY_FAVORITES_ORDER = "favorites_order"
        const val KEY_VISIBLE_ACCOUNTS = "visible_accounts"
        const val KEY_CONTACT_SORT_ORDER = "contact_sort_order"
        const val KEY_CONTACT_DISPLAY_ORDER = "contact_display_order"
        const val KEY_PATREON_PROMPT_SHOWN = "patreon_prompt_shown"
        const val KEY_CALL_RECORDING = "call_recording"
        const val KEY_CALL_RECORDING_AUTO = "call_recording_auto"
        const val KEY_BOTTOM_NAV_ORDER = "bottom_nav_order"
        const val KEY_BOTTOM_NAV_HIDDEN = "bottom_nav_hidden"
        const val KEY_MERGE_FAVORITES_RECENTS = "merge_favorites_recents"
        const val KEY_RECENTS_FAVORITES_COLLAPSED = "recents_favorites_collapsed"

        const val TAB_RECENTS = 0
        const val TAB_FAVORITES = 1
        const val TAB_CONTACTS = 2

        val DEFAULT_BOTTOM_NAV_ORDER = listOf(TAB_RECENTS, TAB_CONTACTS, TAB_FAVORITES)

        // Ever
        const val KEY_BLOCK_UNKNOWN         = "block_unknown_callers"
        const val KEY_BLOCK_HIDDEN          = "block_hidden_callers"
        const val KEY_OPEN_DIALPAD_DEFAULT  = "open_dialpad_default"
        const val KEY_APP_HAPTICS              = "app_haptics_enabled"
        const val KEY_APP_HAPTICS_STRENGTH     = "app_haptics_strength"
        const val KEY_HAPTICS_CUSTOM_INTENSITY = "haptics_custom_intensity"
        const val KEY_NOTES_ENABLED         = "notes_enabled"
        const val KEY_CUSTOM_FONT_PATH      = "custom_font_path"
        const val KEY_CUSTOM_FONT_SIZE      = "custom_font_size"
        const val KEY_THEME_MODE            = "theme_mode"
        const val KEY_BLOCKED_CONTACTS      = "blocked_contacts"
        const val KEY_SHOW_INCOMING_CALL_UI = "show_incoming_call_ui"
        const val KEY_SHOW_CALLER_UI        = "show_caller_ui"
//        const val KEY_SILENCE_UNKNOWN       = "silence_unknown_callers"
        const val KEY_PROXIMITY_BG          = "proximity_sensor_bg"
        const val KEY_SCROLL_HAPTICS        = "scroll_haptics_enabled"
        const val KEY_SCROLL_CM_PER_HAPTIC  = "scroll_cm_per_haptic"   // cm scrolled before each haptic tick
        const val KEY_SCROLL_HAPTICS_PER_CM = "scroll_haptics_per_cm"  // haptic ticks per cm
        const val KEY_SCROLL_HAPTIC_STRENGTH = "scroll_haptic_strength" // vibration amplitude 1–255
        const val KEY_HAPTICS_STRENGTH      = "app_haptics_strength"
        const val KEY_CALL_UI_SHOW_TODAY    = "call_ui_show_today"
        const val KEY_CALL_UI_SHOW_MISSED   = "call_ui_show_missed"
        const val KEY_CALL_UI_SHOW_OUTGOING = "call_ui_show_outgoing"
        const val KEY_CALL_UI_SHOW_CALL_TIME = "call_ui_show_call_time"
        const val KEY_AUTO_UPDATE_CHECK     = "auto_update_check"
        const val KEY_PILL_NAV              = "pill_style_nav"
        const val KEY_FIRST_LAUNCH_DONE     = "first_launch_done"
        // Hangup button width fraction (0.4f .. 1.0f)
        const val KEY_HANGUP_WIDTH          = "hangup_button_width"
        // Dialer role popup shown after welcome
        const val KEY_DIALER_POPUP_SHOWN    = "dialer_popup_shown"
        const val KEY_TELEGRAM_SHOWN        = "telegram_shown"
        const val KEY_SCROLL_ANIMATION      = "scroll_animation_enabled"
        const val KEY_POCKET_MODE_PREVENTION = "pocket_mode_prevention"
        const val KEY_DIRECT_CALL_ON_TAP     = "direct_call_on_tap"
        const val KEY_CONTACTS_DISPLAY_ACCOUNTS = "contacts_display_accounts"
        const val KEY_LIQUID_GLASS              = "liquid_glass_ui"
        const val KEY_LG_BOTTOM_NAV            = "lg_bottom_nav"
        const val KEY_LG_DROPDOWN_MENU         = "lg_dropdown_menu"
        const val KEY_LG_DIALPAD_CALL_BUTTON   = "lg_dialpad_call_button"
        const val KEY_LG_CONTACTS_FAB          = "lg_contacts_fab"
        const val KEY_LG_RECENTS_FAB           = "lg_recents_fab"
        const val KEY_BLUR_EFFECTS            = "blur_effects_ui"
        // Material Blur effect elements
        const val KEY_BLUR_BOTTOM_NAV          = "blur_bottom_nav"
        const val KEY_BLUR_DROPDOWN_MENU       = "blur_dropdown_menu"
        const val KEY_BLUR_DIALPAD_CALL_BUTTON = "blur_dialpad_call_button"
        const val KEY_BLUR_CONTACTS_FAB        = "blur_contacts_fab"
        const val KEY_BLUR_RECENTS_FAB         = "blur_recents_fab"
        const val KEY_AUTO_SPEAKER             = "auto_speaker"
        const val KEY_FLOATING_CALL            = "floating_ongoing_call"
        const val KEY_FLOATING_BUBBLE_X         = "floating_bubble_x"
        const val KEY_FLOATING_BUBBLE_Y         = "floating_bubble_y"
        // Tab Sections visibility
        const val KEY_TAB_SHOW_FAVORITES       = "tab_show_favorites"
        const val KEY_TAB_SHOW_CALLS           = "tab_show_calls"
        const val KEY_TAB_SHOW_CONTACTS        = "tab_show_contacts"
        const val KEY_TAB_SHOW_DIALPAD         = "tab_show_dialpad"
        const val KEY_TAB_SHOW_NOTES           = "tab_show_notes"
        const val KEY_TAB_SHOW_SETTINGS        = "tab_show_settings"
        const val KEY_TAB_SHOW_SEARCH          = "tab_show_search"
        // Comma-separated list of tab keys (favorites, calls, contacts, notes)
        // describing the order tabs appear in the bottom navigation bar.
        const val KEY_TAB_ORDER                = "tab_order"
        const val DEFAULT_TAB_ORDER            = "favorites,contacts,calls,dialpad,notes,search,settings"
        // Biometrics
        const val KEY_BIOMETRICS_TYPE          = "biometrics_type"         // "system" | "pin" | "password" | ""
        const val KEY_BIOMETRICS_PIN           = "biometrics_pin"
        const val KEY_BIOMETRICS_PASSWORD      = "biometrics_password"
        const val KEY_BIOMETRICS_APP_LOCK      = "biometrics_app_lock"
        const val KEY_BIOMETRICS_APP_LOCK_ON_MINIMIZE      = "biometrics_app_lock_on_minimize"
        const val KEY_BIOMETRICS_CALL_LOCK     = "biometrics_call_lock"
        const val KEY_BIOMETRICS_CALL_LOCK_MODE    = "biometrics_call_lock_mode"    // "all" | "specified" | "skip_specified"
        const val KEY_BIOMETRICS_CALL_LOCK_NUMBERS = "biometrics_call_lock_numbers" // comma-separated phone numbers
        // Search filter (Dialpad / Calls / Contacts / Favourites search bars) — the "Filter"
        // button beside the search bar. All four default to true (checked) so search behaves
        // as broadly as possible until the user deliberately narrows it down. Persisted here
        // (rather than in-memory) so the chosen filter survives the app being closed and
        // reopened.
        const val KEY_SEARCH_FILTER_CONTACTS        = "search_filter_contacts"
        const val KEY_SEARCH_FILTER_NON_CONTACTS    = "search_filter_non_contacts"
        const val KEY_SEARCH_FILTER_RECORDINGS      = "search_filter_recordings"
        const val KEY_SEARCH_FILTER_CONTACT_NOTES   = "search_filter_contact_notes"
        const val KEY_SEARCH_FILTER_RECORDING_NOTES = "search_filter_recording_notes"

        // Goodwy
        const val KEY_DEFAULT_TAB              = "default_tab"
        const val KEY_CONTACTS_DEFAULT_ACCOUNT = "contacts_default_accounts"
        const val KEY_LAST_OPENED_TAB          = "last_opened_tab"
        const val KEY_DIALPAD_TEMP_NUMBER      = "dialpad_temp_number"
        const val KEY_IS_PRO_IAP               = "is_pro_iap"
        const val KEY_IS_PRO_SUB               = "is_pro_sub"
        const val KEY_IS_PRO_FOSS              = "is_pro_foss"
        const val KEY_AVATAR_FRAME             = "avatar_frame"
        const val KEY_HIGH_SCORE               = "horse_game_high_score"
        const val KEY_ALWAYS_FULLSCREEN_CALLS  = "always_fullscreen_calls"
        const val KEY_DIALPAD_ANIMATION        = "dialpad_animation_enabled"
        const val KEY_HIDE_VOICE_SEARCH        = "hide_voice_search"
    }
}
