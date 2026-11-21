package com.google.android.diskusage.ui.common

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import timber.log.Timber

/**
 * Central place for the two special permissions the analyser needs.
 *
 * Both are "app ops" and are not listed under *Settings → Apps → Permissions*,
 * which is why the app shows their state itself (menu → Permissions).
 */
object Permissions {

    /** "All files access" on Android 11+, always true on older releases. */
    @JvmStatic
    fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return try {
            Environment.isExternalStorageManager()
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Usage access, needed only for exact per-app data sizes.
     *
     * `AppOpsManager.checkOpNoThrow` reports `MODE_DEFAULT` on several ROMs
     * (MIUI/HyperOS in particular) even after the user enabled the toggle, so a
     * query is used as a second opinion before reporting "denied".
     */
    @JvmStatic
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = try {
            context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        } catch (t: Throwable) {
            null
        }
        if (appOps != null) {
            try {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                    )
                }
                Timber.d("hasUsageAccess: app-op mode = %s", mode)
                when (mode) {
                    AppOpsManager.MODE_ALLOWED -> return true
                    AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> return false
                    // MODE_DEFAULT: the platform has no explicit decision yet, ask
                    // the stats service whether the data is actually readable.
                }
            } catch (t: Throwable) {
                // Fall through to the query below.
            }
        }
        return usageStatsReadable(context)
    }

    private fun usageStatsReadable(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return false
            val now = System.currentTimeMillis()
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 24 * 60 * 60 * 1000L, now
            )
            Timber.d("hasUsageAccess: query returned %s entries", stats?.size ?: -1)
            // An empty list can be legitimate (fresh device), but in that case
            // there is nothing to gain from usage access either.
            !stats.isNullOrEmpty()
        } catch (t: Throwable) {
            false
        }
    }

    @JvmStatic
    fun openAllFilesAccessSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:" + activity.packageName))
            )
        } catch (t: Throwable) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    @JvmStatic
    fun openUsageAccessSettings(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (t: Throwable) {
            // Some ROMs hide the activity; fall back to the app details page.
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + activity.packageName))
                )
            } catch (ignored: Throwable) {
            }
        }
    }
}
