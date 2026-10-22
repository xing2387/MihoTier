package top.xing2387.mihotier.core.config

import android.app.ActivityManager
import android.content.Context
import java.util.Locale

object AppSettingsRepository {
    private const val PREFS_NAME = "clash_app_settings"
    private const val KEY_SHOW_SPEED_NOTIFICATION = "show_speed_notification"
    private const val KEY_EXCLUDE_FROM_RECENTS = "exclude_from_recents"

    fun isShowSpeedNotification(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_SPEED_NOTIFICATION, true)
    }

    fun setShowSpeedNotification(context: Context, show: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_SPEED_NOTIFICATION, show).apply()
    }

    fun isExcludeFromRecents(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EXCLUDE_FROM_RECENTS, false)
    }

    fun setExcludeFromRecents(context: Context, exclude: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EXCLUDE_FROM_RECENTS, exclude).apply()
        applyExcludeFromRecents(context, exclude)
    }

    fun applyExcludeFromRecents(context: Context, exclude: Boolean = isExcludeFromRecents(context)) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.forEach { task ->
                task.setExcludeFromRecents(exclude)
            }
        } catch (_: Exception) {}
    }
}

object BandwidthFormatter {
    /**
     * Bandwidth adaptive formatter:
     * - Uses Bytes, not bits
     * - Adapts units: < 1KB -> B/s, >= 1KB -> KB/s, >= 1MB -> MB/s, >= 1GB -> GB/s
     */
    fun formatAdaptive(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 B/s"
        val kb = 1024.0
        val mb = 1024.0 * 1024.0
        val gb = 1024.0 * 1024.0 * 1024.0

        val b = bytesPerSec.toDouble()
        return when {
            bytesPerSec < 1024L -> "$bytesPerSec B/s"
            b < mb -> {
                val v = b / kb
                if (v >= 1000.0) String.format(Locale.US, "%.1f MB/s", v / 1024.0)
                else String.format(Locale.US, "%.1f KB/s", v)
            }
            b < gb -> {
                val v = b / mb
                if (v >= 1000.0) String.format(Locale.US, "%.2f GB/s", v / 1024.0)
                else String.format(Locale.US, "%.1f MB/s", v)
            }
            else -> String.format(Locale.US, "%.2f GB/s", b / gb)
        }
    }

    fun formatTotalBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = 1024.0 * 1024.0
        val gb = 1024.0 * 1024.0 * 1024.0

        val b = bytes.toDouble()
        return when {
            bytes < 1024L -> "$bytes B"
            b < mb -> String.format(Locale.US, "%.1f KB", b / kb)
            b < gb -> String.format(Locale.US, "%.1f MB", b / mb)
            else -> String.format(Locale.US, "%.2f GB", b / gb)
        }
    }
}
