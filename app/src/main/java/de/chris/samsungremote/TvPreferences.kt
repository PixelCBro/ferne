package de.chris.samsungremote

import android.content.Context

class TvPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tv_remote", Context.MODE_PRIVATE)

    fun load(): TvProfile = TvProfile(
        name = prefs.getString("name", "Mein Samsung TV") ?: "Mein Samsung TV",
        ip = prefs.getString("ip", "") ?: "",
        mac = prefs.getString("mac", "") ?: "",
        token = prefs.getString("token", "") ?: ""
    )

    fun save(profile: TvProfile) {
        prefs.edit()
            .putString("name", profile.name)
            .putString("ip", profile.ip)
            .putString("mac", profile.mac)
            .putString("token", profile.token)
            .apply()
    }

    fun saveToken(token: String) {
        prefs.edit().putString("token", token).apply()
    }
}
