package com.kidfromjupiter.shareby.prefs

import android.content.Context
import android.os.Build

class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDisplayName(): String {
        return prefs.getString(KEY_DISPLAY_NAME, defaultDisplayName()) ?: defaultDisplayName()
    }

    fun setDisplayName(value: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, value.trim()).apply()
    }

    private fun defaultDisplayName(): String {
        return Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
    }

    companion object {
        private const val PREFS_NAME = "shareby_user_prefs"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
