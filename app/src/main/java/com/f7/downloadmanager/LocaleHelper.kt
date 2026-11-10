package com.f7.downloadmanager

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS = "f7_ui"
    private const val KEY_LANGUAGE = "language"
    const val SYSTEM = "system"

    fun savedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "en") ?: "en"

    fun wrap(context: Context): Context {
        val language = savedLanguage(context)
        if (language == SYSTEM) return context
        val locale = runCatching { Locale.forLanguageTag(language) }.getOrDefault(Locale.ENGLISH)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun apply(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (language == SYSTEM) {
                manager.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                manager.applicationLocales = LocaleList.forLanguageTags(language)
            }
        }
    }
}


