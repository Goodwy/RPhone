/*
 * Busy Mode Subsystem
 *
 * Copyright (C) 2024–2026 Goodwy
 *
 * This file is part of Rill Phone.
 *
 * Rill Phone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rill Phone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rill Phone. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.goodwy.rphone.controller.tiles

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.toast

class BusyModeTileService : TileService() {

    private val prefs by lazy { PreferenceManager(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        val isEnabled = prefs.getBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, false)

        if (!isEnabled && !hasSmsPermission()) {
            this.toast(getString(R.string.busy_mode_permission_required))
            openAppSettings()
            return
        }

        val newState = !isEnabled
        prefs.setBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, newState)

        updateTile(isActive = newState)
    }

    override fun onTileAdded() {
        updateTile()
    }

    private fun updateTile(isActive: Boolean? = null) {
        val tile = qsTile ?: return
        val isEnabled = isActive ?: prefs.getBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, false)

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        tile.icon = Icon.createWithResource(this, R.drawable.ic_phone_cancel)

        tile.label = getString(R.string.busy_mode)
        qsTile?.subtitle = if (isEnabled) getString(R.string.on) else getString(R.string.off)

        tile.updateTile()
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Opens the app's system settings (permissions page).
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}