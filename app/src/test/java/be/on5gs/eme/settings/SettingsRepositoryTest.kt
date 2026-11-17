package be.on5gs.eme.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.horizon.Horizon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    /** Een nieuwe installatie heeft geen call en geen locator: die van een ander station tonen klopt niet. */
    @Test
    fun freshInstallHasNoCallsignAndNoLocator() {
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals("", settings.callsign)
        assertEquals("", settings.locator)
    }

    @Test
    fun savedValuesAreKept() {
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings.callsign = "PA3DRK"
        settings.locator = "JO21"
        assertEquals("PA3DRK", settings.callsign)
        assertEquals("JO21", settings.locator)
    }

    @Test
    fun horizonIsKeptAndRubbishFallsBackToNoHorizon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = SettingsRepository(context)
        assertEquals(Horizon(), settings.horizon)                       // nieuwe installatie: geen horizon

        val mine = Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!
        settings.horizon = mine
        assertEquals(mine, SettingsRepository(context).horizon)

        context.getSharedPreferences("eme_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("horizon", "9;onzin").commit()
        assertEquals(Horizon(), SettingsRepository(context).horizon)
    }

    @Test
    fun dishDiameterIsKeptAndRubbishMeansNoDish() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = SettingsRepository(context)
        assertEquals(0.0, settings.dishDiameterM, 0.0)                     // nieuwe installatie: geen schotel
        settings.dishDiameterM = 6.0
        assertEquals(6.0, SettingsRepository(context).dishDiameterM, 0.0)
        context.getSharedPreferences("eme_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("dish_diameter_m", "onzin").commit()
        assertEquals(0.0, SettingsRepository(context).dishDiameterM, 0.0)
    }

    @Test
    fun recentMutualLocatorsAreKeptAndInvalidOnesDropped() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(emptyList<String>(), SettingsRepository(context).mutualLocators)
        SettingsRepository(context).mutualLocators = listOf("FN32", "QF22")
        assertEquals(listOf("FN32", "QF22"), SettingsRepository(context).mutualLocators)
        context.getSharedPreferences("eme_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("mutual_locators", "FN32,xx,,QF22,RE78,JO21,KO85,PM95").commit()
        assertEquals(listOf("FN32", "QF22", "RE78", "JO21", "KO85"), SettingsRepository(context).mutualLocators)
    }

    @Test
    fun dishBandDefaultsTo1296AndIsKept() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(be.on5gs.eme.degradation.EmeBand.B1296, SettingsRepository(context).dishBand)
        SettingsRepository(context).dishBand = be.on5gs.eme.degradation.EmeBand.B432
        assertEquals(be.on5gs.eme.degradation.EmeBand.B432, SettingsRepository(context).dishBand)
    }
}
