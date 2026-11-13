package be.on5gs.eme.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * CredentialStore gebruikt de Android Keystore, waarvoor Robolectric in dit project geen
 * echte cipher-implementatie heeft (NoSuchAlgorithmException voor "AndroidKeyStore" bij een
 * echte save()/key()). Deze tests roepen daarom load()/loadAsync() aan zonder eerst iets op te
 * slaan: decrypt() geeft dan null terug zonder de Keystore aan te raken (zie CredentialStore.kt),
 * zodat de threading van loadAsync() wel degelijk getest kan worden.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CredentialStoreTest {

    private val store get() = CredentialStore(ApplicationProvider.getApplicationContext())

    @Test
    fun loadAsyncReturnsTheSameResultAsLoadWhenNothingIsSaved() {
        val s = store
        assertEquals(s.load(), runBlocking { s.loadAsync() })
    }

    /**
     * De Keystore/cipher-bewerkingen in load() mogen niet op de aanroepende (UI-)draad lopen.
     * Zonder de withContext(Dispatchers.IO) in loadAsync() zou load() hier gewoon op de
     * testdraad zelf uitgevoerd worden, en zou deze test falen.
     */
    @Test
    fun loadAsyncRunsLoadOffTheCallingThread() {
        val s = store
        val callingThreadName = Thread.currentThread().name
        runBlocking { s.loadAsync() }
        assertNotEquals(callingThreadName, CredentialStore.lastLoadThreadNameForTest)
    }
}
