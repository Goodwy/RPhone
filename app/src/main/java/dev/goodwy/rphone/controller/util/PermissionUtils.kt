package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun isAlreadyDefaultDialer(context: Context): Boolean {
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
}

fun getDefaultDialerIntent(context: Context): Intent {
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
}

fun hasReadContacts(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

fun hasCallPhone(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

fun Context.isPackageInstalled(packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    return try {
        packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (_: Exception) {
        false
    }
}