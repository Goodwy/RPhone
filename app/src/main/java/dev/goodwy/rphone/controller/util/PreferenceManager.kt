package dev.goodwy.rphone.controller.util

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.goodwy.rphone.R
import kotlin.time.Duration.Companion.milliseconds

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

    // Internal cache for asynchronous access (populated on initial DataStore collect)
//    This code does not work in some cases, such as when the app icon is changed
//    private val _prefsCache = MutableStateFlow<Preferences>(emptyPreferences())
    // Internal cache for synchronous access with safe timeout on IO dispatcher
    private val _prefsCache = MutableStateFlow<Preferences>(
        runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(200.milliseconds) {
                    dataStore.data.first()
                }
            }
        }.getOrNull() ?: emptyPreferences()
    )

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

    fun getLong(key: String, defaultValue: Long): Long =
        _prefsCache.value[longPreferencesKey(key)] ?: defaultValue

    fun setLong(key: String, value: Long) {
        scope.launch { dataStore.edit { it[longPreferencesKey(key)] = value } }
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

    suspend fun getAllPreferencesSuspend(): Map<String, Any> =
        dataStore.data.first().asMap().mapKeys { it.key.name }.mapValues { it.value }

    suspend fun restoreAllPreferences(preferences: Map<String, Any>) {
        dataStore.edit { prefs ->
            preferences.forEach { (key, value) ->
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(key)] = value
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is Long -> prefs[longPreferencesKey(key)] = value
                    is Float -> prefs[floatPreferencesKey(key)] = value
                    is String -> prefs[stringPreferencesKey(key)] = value
                    is Double -> prefs[floatPreferencesKey(key)] = value.toFloat()
                    is Set<*> -> prefs[stringSetPreferencesKey(key)] =
                        value.mapNotNull { it?.toString() }.toSet()
                }
            }
        }
        _prefsCache.value = dataStore.data.first()
    }

    /** Returns true if an incoming call from [phoneNumber] should be gated behind biometric. */
    fun shouldGateCallWithBiometric(phoneNumber: String?): Boolean {
        if (!getBoolean(KEY_BIOMETRICS_CALL_LOCK, false)) return false

        val type = getString(KEY_BIOMETRICS_TYPE, "") ?: ""
        if (type.isEmpty()) return false
        // On Android 10 and earlier, the system BiometricPrompt does not work on top of the lock screen
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && type == "system") return false

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

    fun getQuickResponses(): List<String> {
        val stored = getString(KEY_QUICK_RESPONSES, null)
        if (stored.isNullOrBlank()) return defaultQuickResponses()
        val list = stored.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
        return list.ifEmpty { defaultQuickResponses() }
    }

    fun setQuickResponses(responses: List<String>) {
        setString(KEY_QUICK_RESPONSES, responses.filter { it.isNotBlank() }.joinToString("|||"))
    }

    fun defaultQuickResponses(): List<String> {
        return listOf(
            appContext.getString(R.string.default_quick_responses_1),
            appContext.getString(R.string.default_quick_responses_2),
            appContext.getString(R.string.default_quick_responses_3),
            appContext.getString(R.string.default_quick_responses_4)
        )
    }

    fun getBlockedPatterns(): List<String> {
        val raw = getString(KEY_BLOCKED_PATTERNS, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optString(i, "").trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setBlockedPatterns(patterns: List<String>) {
        val cleaned = patterns.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val encoded = org.json.JSONArray(cleaned).toString()
        val key = stringPreferencesKey(KEY_BLOCKED_PATTERNS)
        _prefsCache.value = _prefsCache.value.toMutablePreferences().apply {
            this[key] = encoded
        }
        setString(KEY_BLOCKED_PATTERNS, encoded)
    }

    private fun getStringListPref(keyName: String): List<String> {
        val raw = getString(keyName, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optString(i, "").trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun setStringListPref(keyName: String, values: List<String>) {
        val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val encoded = org.json.JSONArray(cleaned).toString()
        val key = stringPreferencesKey(keyName)
        _prefsCache.value = _prefsCache.value.toMutablePreferences().apply {
            this[key] = encoded
        }
        setString(keyName, encoded)
    }

    fun getBusyModeExceptionContactIds(): List<String> =
        getStringListPref(KEY_BUSY_MODE_EXCEPTION_CONTACT_IDS)

    fun setBusyModeExceptionContactIds(contactIds: List<String>) {
        setStringListPref(KEY_BUSY_MODE_EXCEPTION_CONTACT_IDS, contactIds)
    }

    fun getBusyModeExceptionNumbers(): List<String> =
        getStringListPref(KEY_BUSY_MODE_EXCEPTION_NUMBERS)

    fun setBusyModeExceptionNumbers(numbers: List<String>) {
        val normalized = buildList {
            for (raw in numbers) {
                val cleaned = BusyModeExceptionsHelper.normalizeManualNumber(raw)
                if (cleaned.isNotEmpty() && none { BusyModeExceptionsHelper.comparePhoneNumbers(it, cleaned) }) {
                    add(cleaned)
                }
            }
        }
        setStringListPref(KEY_BUSY_MODE_EXCEPTION_NUMBERS, normalized)
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

    fun getCallingCardStyle(contactId: String): String? {
        return try {
            getString(callingCardStyleIdKey(contactId), null)
        } catch (e: Exception) {
            null
        }
    }

    fun getCallingCardStyleForNumber(numberKey: String): String? {
        return try {
            getString(callingCardStyleNumberKey(numberKey), null)
        } catch (e: Exception) {
            null
        }
    }

    fun getCallingCardStyleEntries(): Map<String, String> {
        return try {
            _prefsCache.value.asMap()
                .filterKeys { it.name.startsWith(CALLING_CARD_STYLE_PREFIX) }
                .mapNotNull { (key, value) ->
                    val stringValue = value as? String
                    if (stringValue.isNullOrBlank()) null else key.name to stringValue
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getCallingCardStyleNumberEntries(): Map<String, String> {
        return try {
            _prefsCache.value.asMap()
                .filterKeys { it.name.startsWith(CALLING_CARD_STYLE_NUMBER_PREFIX) }
                .mapNotNull { (key, value) ->
                    val stringValue = value as? String
                    if (stringValue.isNullOrBlank()) null else {
                        val numberKey = key.name.removePrefix(CALLING_CARD_STYLE_NUMBER_PREFIX)
                        numberKey to stringValue
                    }
                }
                .toMap()
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun updateCallingCardStyleEntriesSuspend(updates: Map<String, String?>) {
        if (updates.isEmpty()) return
        dataStore.edit { prefs ->
            updates.forEach { (key, value) ->
                if (key.startsWith(CALLING_CARD_STYLE_PREFIX)) {
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

    // ── Auto Backup Configuration Helpers ─────────────────────────────────
    fun isAutoBackupEnabled(): Boolean = getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
    fun setAutoBackupEnabled(enabled: Boolean) = setBoolean(KEY_AUTO_BACKUP_ENABLED, enabled)

    fun getAutoBackupFolderUri(): String? = getString(KEY_AUTO_BACKUP_FOLDER_URI, null)
    fun setAutoBackupFolderUri(uri: String?) = setString(KEY_AUTO_BACKUP_FOLDER_URI, uri)

    fun getAutoBackupIntervalValue(): Int = getInt(KEY_AUTO_BACKUP_INTERVAL_VALUE, 2)
    fun setAutoBackupIntervalValue(value: Int) = setInt(KEY_AUTO_BACKUP_INTERVAL_VALUE, value)

    fun getAutoBackupIntervalUnit(): String = getString(KEY_AUTO_BACKUP_INTERVAL_UNIT, "hours") ?: "hours"
    fun setAutoBackupIntervalUnit(unit: String) = setString(KEY_AUTO_BACKUP_INTERVAL_UNIT, unit)

    fun isAutoBackupItemSettings(): Boolean = getBoolean(KEY_AUTO_BACKUP_ITEMS_SETTINGS, true)
    fun setAutoBackupItemSettings(enabled: Boolean) = setBoolean(KEY_AUTO_BACKUP_ITEMS_SETTINGS, enabled)

    fun isAutoBackupItemCallingCards(): Boolean = getBoolean(KEY_AUTO_BACKUP_ITEMS_CALLING_CARDS, true)
    fun setAutoBackupItemCallingCards(enabled: Boolean) = setBoolean(KEY_AUTO_BACKUP_ITEMS_CALLING_CARDS, enabled)

    fun isAutoBackupItemNotes(): Boolean = getBoolean(KEY_AUTO_BACKUP_ITEMS_NOTES, true)
    fun setAutoBackupItemNotes(enabled: Boolean) = setBoolean(KEY_AUTO_BACKUP_ITEMS_NOTES, enabled)

    fun isAutoBackupItemContacts(): Boolean = getBoolean(KEY_AUTO_BACKUP_ITEMS_CONTACTS, false)
    fun setAutoBackupItemContacts(enabled: Boolean) = setBoolean(KEY_AUTO_BACKUP_ITEMS_CONTACTS, enabled)

    fun isAutoBackupItemPrivateContacts(): Boolean = getBoolean(KEY_AUTO_BACKUP_ITEMS_PRIVATE_CONTACTS, false)
    fun setAutoBackupItemPrivateContacts(enabled: Boolean) = setBoolean(KEY_AUTO_BACKUP_ITEMS_PRIVATE_CONTACTS, enabled)

    fun isAutoBackupItemCallLogs(): Boolean = getBoolean(KEY_AUTO_BACKUP_ITEMS_CALL_LOGS, false)
    fun setAutoBackupItemCallLogs(enabled: Boolean) = setBoolean(KEY_AUTO_BACKUP_ITEMS_CALL_LOGS, enabled)

    fun isAutoDeleteBackupsTimeEnabled(): Boolean = getBoolean(KEY_AUTO_DELETE_BACKUPS_TIME_ENABLED, false)
    fun setAutoDeleteBackupsTimeEnabled(enabled: Boolean) = setBoolean(KEY_AUTO_DELETE_BACKUPS_TIME_ENABLED, enabled)

    fun getAutoDeleteBackupsTimeValue(): Int = getInt(KEY_AUTO_DELETE_BACKUPS_TIME_VALUE, 7)
    fun setAutoDeleteBackupsTimeValue(value: Int) = setInt(KEY_AUTO_DELETE_BACKUPS_TIME_VALUE, value)

    fun getAutoDeleteBackupsTimeUnit(): String = getString(KEY_AUTO_DELETE_BACKUPS_TIME_UNIT, "days") ?: "days"
    fun setAutoDeleteBackupsTimeUnit(unit: String) = setString(KEY_AUTO_DELETE_BACKUPS_TIME_UNIT, unit)

    fun isAutoDeleteBackupsCountEnabled(): Boolean = getBoolean(KEY_AUTO_DELETE_BACKUPS_COUNT_ENABLED, false)
    fun setAutoDeleteBackupsCountEnabled(enabled: Boolean) = setBoolean(KEY_AUTO_DELETE_BACKUPS_COUNT_ENABLED, enabled)

    fun getAutoDeleteBackupsCountValue(): Int = getInt(KEY_AUTO_DELETE_BACKUPS_COUNT_VALUE, 5)
    fun setAutoDeleteBackupsCountValue(value: Int) = setInt(KEY_AUTO_DELETE_BACKUPS_COUNT_VALUE, value)

    companion object {
        const val CONTACT_BACKGROUND_PREFIX = "contact_background_"
        const val CONTACT_BACKGROUND_NUMBER_PREFIX = "contact_background_num_"
        const val CALLING_CARD_STYLE_PREFIX = "calling_card_style_"
        const val CALLING_CARD_STYLE_NUMBER_PREFIX = "calling_card_style_num_"

        fun callingCardStyleIdKey(contactId: String): String {
            return CALLING_CARD_STYLE_PREFIX + contactId
        }

        fun callingCardStyleNumberKey(numberKey: String): String {
            return CALLING_CARD_STYLE_NUMBER_PREFIX + numberKey
        }

        const val KEY_DEFAULT_SIM           = "default_sim"
        const val KEY_DYNAMIC_COLORS        = "dynamic_colors"
        const val KEY_SHOW_FIRST_LETTER     = "show_first_letter"
        const val KEY_COLORFUL_AVATARS      = "colorful_avatars"
        const val KEY_PRIMARY_COLOR_AVATARS = "primary_color"
        const val KEY_SECONDARY_COLOR_AVATARS = "secondary_color"
        const val KEY_GOOGLE_CONTACTS_AVATARS      = "google_contacts_color"
        const val KEY_SHOW_PICTURE          = "show_picture"
        const val KEY_ICON_ONLY_NAV         = "icon_only_nav"
        const val KEY_DEFAULT_BOTTOM_NAV = "default_bottom_nav"
        const val KEY_DTMF_TONE             = "dtmf_tone"
        const val KEY_SPEED_DIAL            = "speed_dial"
        const val KEY_T9_DIALING            = "t9_dialing"
        const val KEY_PROXIMITY_SENSOR = "proximity_sensor"
        const val KEY_AUTO_SPEAKER_PROXIMITY = "auto_speaker_proximity"
        const val KEY_CONTACTS_TRASH_ENABLED = "contacts_trash_enabled"
        const val KEY_AUTO_REDIAL_BUSY = "auto_redial_busy"
        const val KEY_REDIAL_ATTEMPTS = "redial_attempts"
        const val KEY_REDIAL_DELAY = "redial_delay"
        const val KEY_BLOCK_METHOD = "block_method"
        const val KEY_BLOCK_LOG_VISIBILITY = "block_log_visibility"
        const val KEY_BLOCK_NOTIFICATION = "block_notification"
        const val KEY_BLOCKED_PATTERNS = "blocked_patterns"
        const val KEY_VIBRATE_ON_ANSWER = "vibrate_on_answer"
        const val KEY_VIBRATE_ON_HANGUP = "vibrate_on_hangup"
        const val KEY_DIALPAD_STYLE = "dialpad_style"
        const val KEY_VOICEMAIL_NUMBER = "voicemail_number"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_SILENCE_UNKNOWN = "silence_unknown_calls"
        const val KEY_SPAM_PROTECTION_ENABLED = "spam_protection_enabled"
        const val KEY_SPAM_ACTION = "spam_action"
        const val KEY_SPAM_BLOCK_PRIVATE = "spam_block_private"
        const val KEY_SPAM_STIR_SHAKEN_ENABLED = "spam_stir_shaken_enabled"
        const val KEY_SPAM_BURST_PROTECTION = "spam_burst_protection"
        const val KEY_SPAM_NEIGHBOR_SPOOFING = "spam_neighbor_spoofing"
        const val KEY_SPAM_NOTIFICATION = "spam_notification"
        const val KEY_SPAM_LEGACY_MIGRATED = "spam_legacy_migrated"
        const val KEY_AVATAR_SHAPE = "avatar_shape"
        const val KEY_INCOMING_CALL_UI_MODE = "incoming_call_ui_mode"
        const val KEY_SHOW_CALL_SCREEN_AVATAR = "show_call_screen_avatar"
        const val KEY_HIDE_AVATAR_WITH_BACKGROUND = "hide_avatar_with_background"
        const val KEY_CARD_ROUNDNESS = "card_roundness"
        const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        const val KEY_LAST_USED_ACCOUNT_NAME = "last_used_account_name"
        const val KEY_LAST_USED_ACCOUNT_TYPE = "last_used_account_type"
        const val KEY_FAVORITES_ORDER = "favorites_order"
        const val KEY_VISIBLE_ACCOUNTS = "visible_accounts"
        const val KEY_CONTACT_SORT_ORDER = "contact_sort_order"
        const val KEY_CONTACT_DISPLAY_ORDER = "contact_display_order"

        const val KEY_POCKET_MODE = "pocket_mode"
        const val KEY_BUSY_MODE_ENABLED = "busy_mode_enabled"
        const val KEY_BUSY_MODE_MESSAGE_ENABLED = "busy_mode_message_enabled"
        const val KEY_BUSY_MODE_MESSAGE = "busy_mode_message"
        const val KEY_BUSY_MODE_EXCEPTION_CONTACT_IDS = "busy_mode_exception_contact_ids"
        const val KEY_BUSY_MODE_EXCEPTION_NUMBERS = "busy_mode_exception_numbers"
        const val KEY_HIDE_SAVED_CONTACT_NUMBER = "hide_saved_contact_number"
        const val KEY_QUICK_RESPONSES = "custom_quick_responses"

        const val KEY_RECENTS_FAVORITES_COLLAPSED = "recents_favorites_collapsed"
        const val KEY_IS_SUPPORTER = "is_supporter"
        const val KEY_POST_CALL_SCREEN = "post_call_screen"
        const val KEY_HIDDEN_CONTACTS_VISIBLE = "hidden_contacts_visible"

        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_APP_LOCK_BIOMETRIC = "app_lock_biometric"
        const val KEY_APP_LOCK_PIN = "app_lock_pin"
        const val KEY_APP_LOCK_TIMEOUT = "app_lock_timeout"
        const val APP_LOCK_TIMEOUT_IMMEDIATELY = 0
        const val APP_LOCK_TIMEOUT_1_MIN = 1
        const val APP_LOCK_TIMEOUT_5_MIN = 5
        const val APP_LOCK_TIMEOUT_15_MIN = 15
        const val APP_LOCK_TIMEOUT_30_MIN = 30

        const val KEY_FLOATING_CALL_BUBBLE = "floating_call_bubble"

        const val KEY_FLOATING_BAR_ROUNDNESS = "floating_bar_roundness"
        const val DEFAULT_FLOATING_BAR_ROUNDNESS = 32
        const val KEY_FLOATING_BAR_BLUR = "floating_bar_blur"
        const val KEY_UI_BLUR = "ui_blur"

        const val KEY_SWIPE_ACTIONS_ENABLED = "swipe_actions_enabled"
        const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"
        const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"
        const val KEY_SWIPE_LEFT_SECOND_ACTION = "swipe_left_second_action"

        const val SWIPE_ACTION_NONE = 0
        const val SWIPE_ACTION_CALL = 1
        const val SWIPE_ACTION_MESSAGE = 2
        const val SWIPE_ACTION_VIDEO_CALL = 3
        const val SWIPE_ACTION_WHATSAPP = 4
        const val SWIPE_ACTION_COPY_NUMBER = 5
        const val SWIPE_ACTION_DELETE = 6
        const val SWIPE_ACTION_TELEGRAM = 7

        const val KEY_MISSED_CALL_CARD = "missed_call_card"
        const val KEY_AUTO_DECLINE_UNKNOWN = "auto_decline_unknown"
        const val KEY_AUTO_DECLINE_NON_CONTACTS = "auto_decline_non_contacts"
        const val KEY_DUAL_SIM_DIALPAD_BUTTONS = "dual_sim_dialpad_buttons"

        const val KEY_CALL_LOG_LIMIT = "call_log_limit"
        const val CALL_LOG_LIMIT_DEFAULT = 1000
        const val KEY_AUTO_PASTE_CLIPBOARD = "auto_paste_clipboard"
        const val KEY_CALL_ANALYTICS_TRACKING = "call_analytics_tracking"

        // Ever
        const val KEY_APP_HAPTICS              = "app_haptics_enabled"
        const val KEY_APP_HAPTICS_STRENGTH     = "app_haptics_strength"
        const val KEY_HAPTICS_CUSTOM_INTENSITY = "haptics_custom_intensity"
        const val KEY_CUSTOM_FONT_PATH      = "custom_font_path"
        const val KEY_CUSTOM_FONT_SIZE      = "custom_font_size"
        const val KEY_THEME_MODE            = "theme_mode"
        const val KEY_BLOCKED_CONTACTS      = "blocked_contacts"
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
        // Hangup button width fraction (0.4f .. 1.0f)
        const val KEY_HANGUP_WIDTH          = "hangup_button_width"
        // Dialer role popup shown after welcome
        const val KEY_SCROLL_ANIMATION      = "scroll_animation_enabled"
        const val KEY_DIRECT_CALL_ON_TAP     = "direct_call_on_tap"
        const val KEY_LIQUID_GLASS              = "liquid_glass_ui"
        const val KEY_LG_BOTTOM_NAV            = "lg_bottom_nav"
        const val KEY_LG_DROPDOWN_MENU         = "lg_dropdown_menu"
        const val KEY_LG_DIALPAD_CALL_BUTTON   = "lg_dialpad_call_button"
        const val KEY_LG_CONTACTS_FAB          = "lg_contacts_fab"
        const val KEY_LG_RECENTS_FAB           = "lg_recents_fab"
        const val KEY_LG_CALL_SCREEN           = "lg_call_screen"
        const val KEY_LG_DIALPAD               = "lg_dialpad"
        const val KEY_BLUR_EFFECTS             = "blur_effects_ui"
        const val KEY_BLUR_INTENSITY             = "blur_intensity"
        // Material Blur effect elements
        const val KEY_BLUR_BOTTOM_NAV          = "blur_bottom_nav"
        const val KEY_BLUR_DROPDOWN_MENU       = "blur_dropdown_menu"
        const val KEY_BLUR_DIALPAD_CALL_BUTTON = "blur_dialpad_call_button"
        const val KEY_BLUR_CONTACTS_FAB        = "blur_contacts_fab"
        const val KEY_BLUR_RECENTS_FAB         = "blur_recents_fab"
        const val KEY_BLUR_CALL_SCREEN         = "blur_call_screen"
        const val KEY_BLUR_DIALPAD             = "BLUR_dialpad"
        const val KEY_AUTO_SPEAKER             = "auto_speaker"
        const val KEY_FLOATING_CALL            = "floating_ongoing_call"
        const val KEY_FLOATING_BUBBLE_X         = "floating_bubble_x"
        const val KEY_FLOATING_BUBBLE_Y         = "floating_bubble_y"
        // ── Auto Backup Configuration ───────────────────────────────────────
        const val KEY_AUTO_BACKUP_ENABLED              = "auto_backup_enabled"
        const val KEY_AUTO_BACKUP_FOLDER_URI           = "auto_backup_folder_uri"
        const val KEY_AUTO_BACKUP_INTERVAL_VALUE       = "auto_backup_interval_value" // default: 2
        const val KEY_AUTO_BACKUP_INTERVAL_UNIT        = "auto_backup_interval_unit"  // default: "hours"
        const val KEY_AUTO_BACKUP_ITEMS_SETTINGS       = "auto_backup_items_settings"
        const val KEY_AUTO_BACKUP_ITEMS_CALLING_CARDS  = "auto_backup_items_calling_cards"
        const val KEY_AUTO_BACKUP_ITEMS_NOTES          = "auto_backup_items_notes"
        const val KEY_AUTO_BACKUP_ITEMS_CONTACTS       = "auto_backup_items_contacts"
        const val KEY_AUTO_BACKUP_ITEMS_PRIVATE_CONTACTS = "auto_backup_items_private_contacts"
        const val KEY_AUTO_BACKUP_ITEMS_CALL_LOGS      = "auto_backup_items_call_logs"
        // Auto Delete Backups Configuration
        const val KEY_AUTO_DELETE_BACKUPS_TIME_ENABLED = "auto_delete_backups_time_enabled"
        const val KEY_AUTO_DELETE_BACKUPS_TIME_VALUE   = "auto_delete_backups_time_value" // default: 7
        const val KEY_AUTO_DELETE_BACKUPS_TIME_UNIT    = "auto_delete_backups_time_unit"  // default: "days"
        const val KEY_AUTO_DELETE_BACKUPS_COUNT_ENABLED = "auto_delete_backups_count_enabled"
        const val KEY_AUTO_DELETE_BACKUPS_COUNT_VALUE   = "auto_delete_backups_count_value" // default: 5
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
        const val KEY_BIOMETRICS_CALL_LOCK     = "biometrics_call_lock"
        const val KEY_BIOMETRICS_CALL_LOCK_MODE    = "biometrics_call_lock_mode"    // "all" | "specified" | "skip_specified"
        const val KEY_BIOMETRICS_CALL_LOCK_NUMBERS = "biometrics_call_lock_numbers" // comma-separated phone numbers
        // Updates — version tag of the APK currently sitting in Downloads (if any)
        const val KEY_DOWNLOADED_UPDATE_VERSION = "downloaded_update_version"
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

        const val KEY_APP_NAME_PRESET = "app_name_preset"

        // Flash on Call
        const val KEY_FLASH_ON_CALL_ENABLED    = "flash_on_call_enabled"
        const val KEY_FLASH_ON_CALL_MODE       = "flash_on_call_mode" // 0 = Always, 1 = Silent/Vibrate Only

        const val KEY_DEFAULT_TAB              = "default_tab"
        const val KEY_LAST_OPENED_TAB          = "last_opened_tab"
        const val KEY_IS_PRO_IAP               = "is_pro_iap"
        const val KEY_IS_PRO_SUB               = "is_pro_sub"
        const val KEY_IS_PRO_FOSS              = "is_pro_foss"
        const val KEY_AVATAR_FRAME             = "avatar_frame"
        const val KEY_HIGH_SCORE               = "horse_game_high_score"
        const val KEY_ALWAYS_FULLSCREEN_CALLS  = "always_fullscreen_calls"
        const val KEY_OPEN_CALL_SCREEN_FROM_NOTIFICATION  = "open_call_screen_from_notification"
        const val KEY_DIALPAD_ANIMATION        = "dialpad_animation_enabled"
        const val KEY_DIALPAD_HIDE_LETTERS     = "dialpad_hide_letters"
        const val KEY_HIDE_VOICE_SEARCH        = "hide_voice_search"
        const val KEY_INCLUDE_DAY_OF_WEEK      = "include_day_of_week"

        const val KEY_FSI_BANNER_SNOOZED_UNTIL = "fsi_banner_snoozed_until"
    }

    fun isAlwaysFullscreenCalls(): Boolean = getBoolean(KEY_ALWAYS_FULLSCREEN_CALLS, false)
    fun setAlwaysFullscreenCalls(enabled: Boolean) = setBoolean(KEY_ALWAYS_FULLSCREEN_CALLS, enabled)
    fun isOpenCallScreen(): Boolean = getBoolean(KEY_OPEN_CALL_SCREEN_FROM_NOTIFICATION, true)
    fun setOpenCallScreen(enabled: Boolean) = setBoolean(KEY_OPEN_CALL_SCREEN_FROM_NOTIFICATION, enabled)
    fun isHideSavedContactNumberEnabled(): Boolean = getBoolean(KEY_HIDE_SAVED_CONTACT_NUMBER, false)
    fun setHideSavedContactNumberEnabled(enabled: Boolean) = setBoolean(KEY_HIDE_SAVED_CONTACT_NUMBER, enabled)

    fun getCallLogLimit(): Int = getInt(KEY_CALL_LOG_LIMIT, CALL_LOG_LIMIT_DEFAULT)
    fun setCallLogLimit(limit: Int) = setInt(KEY_CALL_LOG_LIMIT, limit)

    fun isAutoPasteClipboardEnabled(): Boolean = getBoolean(KEY_AUTO_PASTE_CLIPBOARD, false)
    fun setAutoPasteClipboardEnabled(enabled: Boolean) = setBoolean(KEY_AUTO_PASTE_CLIPBOARD, enabled)

    fun isCallAnalyticsTrackingEnabled(): Boolean = getBoolean(KEY_CALL_ANALYTICS_TRACKING, true)
    fun setCallAnalyticsTrackingEnabled(enabled: Boolean) = setBoolean(KEY_CALL_ANALYTICS_TRACKING, enabled)

    fun isAppLockEnabled(): Boolean = getBoolean(KEY_BIOMETRICS_APP_LOCK, false) && getString(KEY_BIOMETRICS_TYPE, "")?.isNotEmpty() ?: false
    fun setAppLockEnabled(enabled: Boolean) = setBoolean(KEY_APP_LOCK_ENABLED, enabled)
    fun isBiometricLockEnabled(): Boolean = getBoolean(KEY_APP_LOCK_BIOMETRIC, true)
    fun setBiometricLockEnabled(enabled: Boolean) = setBoolean(KEY_APP_LOCK_BIOMETRIC, enabled)
    fun getAppLockPin(): String = getString(KEY_APP_LOCK_PIN, "") ?: ""
    fun setAppLockPin(pin: String) = setString(KEY_APP_LOCK_PIN, pin)
    fun getAppLockTimeout(): Int = getInt(KEY_APP_LOCK_TIMEOUT, APP_LOCK_TIMEOUT_IMMEDIATELY)
    fun setAppLockTimeout(timeout: Int) = setInt(KEY_APP_LOCK_TIMEOUT, timeout)
    fun isFloatingCallBubbleEnabled(): Boolean = getBoolean(KEY_FLOATING_CALL_BUBBLE, true)
    fun setFloatingCallBubbleEnabled(enabled: Boolean) = setBoolean(KEY_FLOATING_CALL_BUBBLE, enabled)
    fun isHiddenContactsVisible(): Boolean = getBoolean(KEY_HIDDEN_CONTACTS_VISIBLE, false)
    fun setHiddenContactsVisible(visible: Boolean) = setBoolean(KEY_HIDDEN_CONTACTS_VISIBLE, visible)

    fun isUiBlurEnabled(): Boolean = getBoolean(KEY_UI_BLUR, getBoolean(KEY_FLOATING_BAR_BLUR, false))
    fun setUiBlurEnabled(enabled: Boolean) {
        setBoolean(KEY_UI_BLUR, enabled)
        setBoolean(KEY_FLOATING_BAR_BLUR, enabled)
    }

    fun isSwipeActionsEnabled(): Boolean = getBoolean(KEY_SWIPE_ACTIONS_ENABLED, true)
    fun setSwipeActionsEnabled(enabled: Boolean) = setBoolean(KEY_SWIPE_ACTIONS_ENABLED, enabled)

    fun getSwipeRightAction(): Int = getInt(KEY_SWIPE_RIGHT_ACTION, SWIPE_ACTION_CALL)
    fun setSwipeRightAction(action: Int) = setInt(KEY_SWIPE_RIGHT_ACTION, action)

    fun getSwipeLeftAction(): Int = getInt(KEY_SWIPE_LEFT_ACTION, SWIPE_ACTION_MESSAGE)
    fun setSwipeLeftAction(action: Int) = setInt(KEY_SWIPE_LEFT_ACTION, action)

    fun getSwipeLeftSecondAction(): Int = getInt(KEY_SWIPE_LEFT_SECOND_ACTION, SWIPE_ACTION_DELETE)
    fun setSwipeLeftSecondAction(action: Int) = setInt(KEY_SWIPE_LEFT_SECOND_ACTION, action)

    fun isContactsTrashEnabled(): Boolean = getBoolean(KEY_CONTACTS_TRASH_ENABLED, true)
    fun setContactsTrashEnabled(enabled: Boolean) = setBoolean(KEY_CONTACTS_TRASH_ENABLED, enabled)

    fun resetSwipeActions() {
        setInt(KEY_SWIPE_RIGHT_ACTION, SWIPE_ACTION_CALL)
        setInt(KEY_SWIPE_LEFT_ACTION, SWIPE_ACTION_MESSAGE)
        setInt(KEY_SWIPE_LEFT_SECOND_ACTION, SWIPE_ACTION_DELETE)
    }
}
