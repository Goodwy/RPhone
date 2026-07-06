package dev.goodwy.rphone

import android.app.Application
import dev.goodwy.rphone.view.screen.settings.KEY_SELECTED_APP_ICON
import dev.goodwy.rphone.view.screen.settings.applyIcon
import dev.goodwy.rphone.view.screen.settings.buildIcons
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RillApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RillApp)
            modules(appModule)
        }
        restoreSavedAppIcon()
    }

    private fun restoreSavedAppIcon() {
        try {
            val prefs = getSharedPreferences("rill_prefs", MODE_PRIVATE)
            val savedKey = prefs.getString(KEY_SELECTED_APP_ICON, "default") ?: "default"
            val icons = buildIcons(this)
            val entry = icons.find { it.key == savedKey } ?: icons.first()
            applyIcon(this, icons, entry)
        } catch (_: Exception) {}
    }
}
