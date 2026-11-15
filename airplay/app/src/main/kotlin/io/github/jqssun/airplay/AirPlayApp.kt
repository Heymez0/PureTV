package io.github.jqssun.airplay

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AirPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PureTV: before any service, receiver or screen reads the name
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.NAME_SET, false)) {
            prefs.edit().putString(Prefs.SERVER_NAME.key, Prefs.SERVER_NAME.def).putBoolean(Prefs.NAME_SET, true).commit()
        }
    }
}
