package com.music.bitchord.lockscreen

import android.app.Application
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.music.bitchord.data.settings.AppSettings

/**
 * Brings the full-screen lock-screen player up when the phone is woken while
 * music is playing.
 *
 * Android has no way to replace the system's lock-screen media card, so the
 * player is an Activity that shows *over* the keyguard ([LockScreenActivity]).
 * Something has to start it, and `SCREEN_ON` cannot be declared in the manifest,
 * so a receiver is registered for the life of the process. That is long enough:
 * the process is kept alive by [PlaybackService][com.music.bitchord.playback.PlaybackService]'s
 * foreground notification for exactly as long as there is music to show.
 *
 * Starting an Activity from the background is blocked on Android 10+ unless the
 * app holds "Display over other apps", which is therefore what the setting asks
 * for. Without it this does nothing, silently.
 */
object LockScreenLauncher {

    private var installed = false

    /** Call once, after [AppSettings.init]. */
    fun install(app: Application) {
        if (installed) return
        installed = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!AppSettings.lockScreenPlayer.value) return
                if (!Settings.canDrawOverlays(context)) return
                val keyguard = context.getSystemService(KeyguardManager::class.java)
                if (keyguard?.isKeyguardLocked != true) return
                // A cheap "is anything playing" gate. It cannot tell whose music
                // it is, so the Activity checks its own player and closes itself
                // straight away if BitChord is not the one playing.
                val audio = context.getSystemService(AudioManager::class.java)
                if (audio?.isMusicActive != true) return
                runCatching {
                    context.startActivity(
                        Intent(context, LockScreenActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    )
                }
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * Opens the system page where the listener grants "Display over other
     * apps" to BitChord, unless it is already granted.
     */
    fun requestOverlayPermission(context: Context) {
        if (Settings.canDrawOverlays(context)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some OEM builds hide the page; the setting then simply stays inert.
        }
    }
}
