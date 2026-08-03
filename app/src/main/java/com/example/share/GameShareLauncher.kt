package com.example.share

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

private const val CHOSEN_TARGET_ACTION = "com.example.terrafill.SHARE_TARGET_CHOSEN"

/**
 * Opens Android's share sheet for [text] and calls [onTargetChosen] once - only if
 * the player actually picks an app to share with, never just for opening the sheet
 * and never if they back out of it.
 *
 * This is the ceiling of what's verifiable client-side: Android has no API to
 * confirm a share was actually sent inside the target app (e.g. that a WhatsApp
 * message was really delivered), only which app the player picked via the chooser's
 * [Intent.EXTRA_CHOSEN_COMPONENT] callback. Good enough to gate a one-time reward
 * against the trivial "did nothing" case, not meant to be tamper-proof.
 */
fun launchGameShare(activity: Activity, text: String, onTargetChosen: () -> Unit) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

    // The chooser reports which app was picked by broadcasting back to a
    // PendingIntent we supply - it must stay mutable so the system can attach the
    // EXTRA_CHOSEN_COMPONENT extra to it before firing.
    val callbackIntent = Intent(CHOSEN_TARGET_ACTION).setPackage(activity.packageName)
    val pendingIntent = PendingIntent.getBroadcast(
        activity,
        0,
        callbackIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            context.unregisterReceiver(this)
            val chosen = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
            if (chosen != null) onTargetChosen()
        }
    }
    ContextCompat.registerReceiver(
        activity,
        receiver,
        IntentFilter(CHOSEN_TARGET_ACTION),
        ContextCompat.RECEIVER_NOT_EXPORTED
    )

    activity.startActivity(Intent.createChooser(sendIntent, null, pendingIntent.intentSender))
}
