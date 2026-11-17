package be.on5gs.eme.settings

import be.on5gs.eme.location.Maidenhead
import android.content.Context
import be.on5gs.eme.chat.Hb9qBand
import be.on5gs.eme.cq.Band
import be.on5gs.eme.degradation.EmeBand
import be.on5gs.eme.horizon.Horizon

/** Bewaart gewone (niet-geheime) instellingen op het toestel. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("eme_settings", Context.MODE_PRIVATE)

    var locator: String
        get() = prefs.getString(KEY_LOCATOR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCATOR, value).apply()

    var callsign: String
        get() = prefs.getString(KEY_CALLSIGN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CALLSIGN, value).apply()

    /** Gekozen banden in de Live CQ-lijst. Standaard alleen 23cm. */
    var cqBands: Set<Band>
        get() {
            val names = prefs.getStringSet(KEY_CQ_BANDS, null) ?: return setOf(Band.B23CM)
            return names.mapNotNull { n -> Band.entries.firstOrNull { it.name == n } }.toSet()
        }
        set(value) = prefs.edit().putStringSet(KEY_CQ_BANDS, value.map { it.name }.toSet()).apply()

    /** Gekozen band in de HB9Q-chat. Standaard 1296. */
    var chatBand: Hb9qBand
        get() = Hb9qBand.entries.firstOrNull { it.name == prefs.getString(KEY_CHAT_BAND, null) } ?: Hb9qBand.B1296
        set(value) = prefs.edit().putString(KEY_CHAT_BAND, value.name).apply()

    /** Gekozen band in het tabblad Degradatie. Standaard 1296. */
    var degBand: EmeBand
        get() = EmeBand.entries.firstOrNull { it.name == prefs.getString(KEY_DEG_BAND, null) } ?: EmeBand.B1296
        set(value) = prefs.edit().putString(KEY_DEG_BAND, value.name).apply()

    /**
     * Eigen horizon (ondergrens en hindernissen), zie horizon/Horizon.kt. Standaard of bij een
     * onleesbare waarde: geen hindernissen, dus de maan is bruikbaar zodra ze opgekomen is.
     */
    var horizon: Horizon
        get() = Horizon.decode(prefs.getString(KEY_HORIZON, null)) ?: Horizon()
        set(value) = prefs.edit().putString(KEY_HORIZON, value.encode()).apply()

    /** Diameter van de schotel in meter voor de marge boven de eigen horizon; 0 = geen marge (bv. yagi's). */
    var dishDiameterM: Double
        get() = prefs.getString(KEY_DISH, null)?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 } ?: 0.0
        set(value) = prefs.edit().putString(KEY_DISH, value.toString()).apply()

    /** Band waarop de schotel gebruikt wordt, voor de marge (los van de band in de Deg-tab). Standaard 1296. */
    var dishBand: EmeBand
        get() = EmeBand.entries.firstOrNull { it.name == prefs.getString(KEY_DISH_BAND, null) } ?: EmeBand.B1296
        set(value) = prefs.edit().putString(KEY_DISH_BAND, value.name).apply()

    /** De laatst gebruikte locators in het mutual window (nieuwste vooraan, hoogstens 5); onleesbare vallen weg. */
    var mutualLocators: List<String>
        get() = (prefs.getString(KEY_MUTUAL, null) ?: "").split(",").filter { Maidenhead.isValid(it) && it.isNotEmpty() }.take(5)
        set(value) = prefs.edit().putString(KEY_MUTUAL, value.take(5).joinToString(",")).apply()

    /** Eén keer per dag aan GitHub vragen of er een nieuwe versie is (#126). Standaard aan. */
    var updateCheck: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_UPDATE_CHECK, value).apply()

    /** Tijdstip van de laatste vraag aan GitHub, ook een mislukte. */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    /** Laatste versie op GitHub bij die vraag ("1.4.0"), leeg als nog onbekend. */
    var latestVersion: String
        get() = prefs.getString(KEY_LATEST_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LATEST_VERSION, value).apply()

    /** Versie waarvan de gebruiker de melding sloot; die komt niet terug, een nieuwere wel. */
    var dismissedVersion: String
        get() = prefs.getString(KEY_DISMISSED_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DISMISSED_VERSION, value).apply()

    companion object {
        private const val KEY_MUTUAL = "mutual_locators"
        private const val KEY_UPDATE_CHECK = "update_check"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_DISMISSED_VERSION = "dismissed_version"
        private const val KEY_DISH_BAND = "dish_band"
        private const val KEY_DISH = "dish_diameter_m"
        private const val KEY_HORIZON = "horizon"
        private const val KEY_DEG_BAND = "deg_band"
        private const val KEY_CHAT_BAND = "chat_band"
        private const val KEY_LOCATOR = "locator"
        private const val KEY_CALLSIGN = "callsign"
        private const val KEY_CQ_BANDS = "cq_bands"
    }
}
