package exh.ui.lock

import android.annotation.TargetApi
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import kotlin.experimental.and


/**
 * Password hashing utils
 */

/**
 * Yes, I know SHA512 is fast, but bcrypt on mobile devices is too slow apparently
 */
fun sha512(passwordToHash: String, salt: String): String {
    val md = MessageDigest.getInstance("SHA-512")
    md.update(salt.toByteArray(charset("UTF-8")))
    val bytes = md.digest(passwordToHash.toByteArray(charset("UTF-8")))
    val sb = StringBuilder()
    for (i in bytes.indices) {
        sb.append(Integer.toString((bytes[i] and 0xff.toByte()) + 0x100, 16).substring(1))
    }
    return sb.toString()
}

/**
 * Check if lock is enabled
 */
fun lockEnabled(prefs: PreferencesHelper = Injekt.get())
    = prefs.lockHash().get() != null
            && prefs.lockSalt().get() != null
            && prefs.lockLength().getOrDefault() != -1

/**
 * Check if the lock will function properly
 *
 * @return true if action is required, false if lock is working properly
 */
fun notifyLockSecurity(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !hasAccessToUsageStats(context)) {
        MaterialDialog.Builder(context)
                .title("Permission required")
                .content("${context.getString(R.string.app_name)} requires the usage stats permission to detect when you leave the app. " +
                        "This is required for the application lock to function properly. " +
                        "Press OK to grant this permission now.")
                .negativeText("Cancel")
                .positiveText("Ok")
                .onPositive { _, _ ->
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .autoDismiss(true)
                .cancelable(false)
                .show()
        return true
    } else {
        return false
    }
}

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
fun hasAccessToUsageStats(context: Context): Boolean {
    try {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName)
        return (mode == AppOpsManager.MODE_ALLOWED)
    } catch (e: PackageManager.NameNotFoundException) {
        return false
    }
}
