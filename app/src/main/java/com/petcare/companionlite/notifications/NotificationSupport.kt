package com.petcare.companionlite.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.petcare.companionlite.MainActivity
import com.petcare.companionlite.R

/** Shared permission gate, brand look and deep-link PendingIntents for every app notification. */
object NotificationSupport {
    /** Sage brand color, tints the small icon and app name in the notification header. */
    val BRAND_COLOR: Int = 0xFF84B59F.toInt()

    /**
     * True when the user has not blocked notifications for the app (checked on every API level)
     * and, on Android 13+, the POST_NOTIFICATIONS runtime permission is granted.
     */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Builder with the monochrome paw small icon and the brand color. */
    fun builder(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setColor(BRAND_COLOR)

    /**
     * PendingIntent that opens MainActivity. [uri] must be unique per notification: extras are not
     * part of PendingIntent identity, so the data URI is what stops FLAG_UPDATE_CURRENT from
     * overwriting another notification's extras.
     */
    fun openAppIntent(context: Context, requestCode: Int, uri: Uri, configure: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            configure()
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
