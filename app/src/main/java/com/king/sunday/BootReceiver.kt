package com.king.sunday

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a phone restart, bring back the floating orb / "Hey Sunday" if they were switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PresenceService.sync(context)
        }
    }
}
