package be.on5gs.eme.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Taalkeuze van de app (tandwiel-menu). "" = taal van de telefoon.
 * De gekozen taal wordt bij het starten van het scherm toegepast (zie MainActivity).
 */
object AppLanguage {

    /** Taalcode -> naam in de eigen taal. */
    val options: List<Pair<String, String>> = listOf(
        "nl" to "Nederlands",
        "fr" to "Français",
        "en" to "English",
        "de" to "Deutsch",
        "es" to "Español",
        "it" to "Italiano",
        "pt" to "Português",
        "sv" to "Svenska",
        "da" to "Dansk",
        "fi" to "Suomi",
        "cs" to "Čeština",
        "ru" to "Русский",
        "uk" to "Українська",
        "zh" to "中文",
        "ja" to "日本語",
    )

    private const val PREFS = "eme_settings"
    private const val KEY = "language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /** Geeft een context terug met de gekozen taal (of de originele bij "taal van de telefoon"). */
    fun wrap(base: Context): Context {
        val code = get(base)
        if (code.isEmpty()) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
