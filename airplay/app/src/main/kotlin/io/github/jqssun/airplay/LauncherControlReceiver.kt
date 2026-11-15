package io.github.jqssun.airplay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.jqssun.airplay.service.AirPlayService
import org.json.JSONObject

/**
 * Explicit, signature-protected commands. Never accepts preference values, URLs or shell text: ALWAYS_ON and ON_DEMAND
 * only switch Start server at boot (PureTV's Connections), the one receiver setting the launcher may change.
 */
class LauncherControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != QUERY && action != START && action != STOP && action != ALWAYS_ON && action != ON_DEMAND) return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        var state: String? = null
        try {
            when (action) {
                START -> {
                    LauncherControl.setDisabled(context, false)
                    ContextCompat.startForegroundService(context,
                        Intent(context, AirPlayService::class.java).setAction(AirPlayService.ACTION_START_SERVER))
                    state = "starting"
                }
                STOP -> {
                    LauncherControl.setDisabled(context, true)
                    AirPlayService.launcherInstance?.stopServer()
                }
                // the running service follows the change itself (AirPlayService observes the preference)
                ALWAYS_ON, ON_DEMAND -> prefs.edit().putBoolean(Prefs.BOOT_AUTO_START.key, action == ALWAYS_ON).commit()
            }
            val service = AirPlayService.launcherInstance
            val actual = service?.serverState?.value?.name?.lowercase() ?: "stopped"
            setResult(Activity.RESULT_OK, JSONObject().put("state", if (actual == "running") actual else state ?: actual)
                .put("connections", service?.connectionCount?.value ?: 0)
                .put("disabled", LauncherControl.disabled(context))
                .put("always", prefs[Prefs.BOOT_AUTO_START]).toString(), null)
        } catch (_: RuntimeException) {
            setResult(Activity.RESULT_CANCELED, "{\"state\":\"error\"}", null)
        }
    }

    companion object {
        const val QUERY = "io.puretv.airplay.control.QUERY"
        const val START = "io.puretv.airplay.control.START"
        const val STOP = "io.puretv.airplay.control.STOP"
        const val ALWAYS_ON = "io.puretv.airplay.control.ALWAYS_ON"
        const val ON_DEMAND = "io.puretv.airplay.control.ON_DEMAND"
    }
}

/** An explicit stop stays stopped across opening the UI and reboot, without editing receiver preferences. */
object LauncherControl {
    fun disabled(context: Context) = context.getSharedPreferences("launcher_control", Context.MODE_PRIVATE)
        .getBoolean("disabled", false)
    fun setDisabled(context: Context, value: Boolean) {
        context.getSharedPreferences("launcher_control", Context.MODE_PRIVATE).edit().putBoolean("disabled", value).commit()
    }
}
