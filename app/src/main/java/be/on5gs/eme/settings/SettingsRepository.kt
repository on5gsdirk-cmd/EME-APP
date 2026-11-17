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

    companion object {
        private const val KEY_MUTUAL = "mutual_locators"
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
