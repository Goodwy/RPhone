package dev.goodwy.rphone.controller

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import dev.goodwy.rphone.controller.util.makeCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class NavigationTarget {
    object Recents : NavigationTarget()
    data class Dialpad(val number: String) : NavigationTarget()
    data class ContactDetails(val contactId: String) : NavigationTarget()
    data class ContactEdit(val contactId: String? = null, val initialName: String? = null, val initialPhone: String? = null) : NavigationTarget()
}

class MainViewModel(private val prefs: PreferenceManager) : ViewModel() {

    private var isInBackground = false

    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked = _isUnlocked.asStateFlow()

    init {
        val biometricType = prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
        val appLockEnabled = prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)
        _isUnlocked.value = !(biometricType.isNotEmpty() && appLockEnabled)
    }

    fun onResume() {
        val appLockEnabled = prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)
        val lockOnMinimize = prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK_ON_MINIMIZE, false)
        if (appLockEnabled && lockOnMinimize && isInBackground) {
            _isUnlocked.value = false
        }
        isInBackground = false
    }

    fun onStop() {
        val appLockEnabled = prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)
        val lockOnMinimize = prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK_ON_MINIMIZE, false)
        if (appLockEnabled && lockOnMinimize) {
            isInBackground = true
        }
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun getNavigationTarget(intent: Intent, context: Context): NavigationTarget? {
        val data = intent.data
        val action = intent.action ?: return null

        return when (action) {
            "dev.goodwy.rphone.ACTION_VIEW_RECENTS" -> NavigationTarget.Recents
            Intent.ACTION_VIEW -> {
                val mimeType = intent.type
                if (mimeType == "vnd.android.cursor.dir/calls" ||
                    data?.toString()?.contains("call_log") == true ||
                    data?.toString()?.contains("calls") == true) {
                    NavigationTarget.Recents
                } else if (data?.scheme == "tel") {
                    NavigationTarget.Dialpad(data.schemeSpecificPart)
                } else if (data?.toString()?.contains("contacts") == true ||
                    data?.toString()?.contains("com.android.contacts") == true ||
                    intent.hasExtra("contact_id")) {
                    val id = data?.lastPathSegment ?: intent.getStringExtra("contact_id")
                    if (id != null) {
                        NavigationTarget.ContactDetails(id)
                    } else null
                } else null
            }
            Intent.ACTION_DIAL -> {
                if (data?.scheme == "tel") {
                    NavigationTarget.Dialpad(data.schemeSpecificPart)
                } else null
            }
            Intent.ACTION_CALL -> {
                if (data?.scheme == "tel") {
                    val number = data.schemeSpecificPart
                    if (isAlreadyDefaultDialer(context)) {
                        makeCall(context, number = number)
                        null
                    } else {
                        NavigationTarget.Dialpad(number)
                    }
                } else null
            }
            Intent.ACTION_INSERT -> {
                val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME)
                val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
                NavigationTarget.ContactEdit(initialName = name, initialPhone = phone)
            }
            Intent.ACTION_EDIT -> {
                val id = data?.lastPathSegment
                if (id != null) {
                    NavigationTarget.ContactEdit(contactId = id)
                } else null
            }
            else -> null
        }
    }
}