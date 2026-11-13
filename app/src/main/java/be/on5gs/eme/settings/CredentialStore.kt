package be.on5gs.eme.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bewaart de HB9Q-login VERSLEUTELD op het toestel.
 * De sleutel zit in de Android Keystore (hardware-beveiligd waar mogelijk) en kan
 * het toestel niet verlaten. Er staat nooit een wachtwoord in platte tekst of in de code.
 */
class CredentialStore(context: Context) {

    data class Credentials(val login: String, val password: String)

    private val prefs = context.getSharedPreferences("eme_secure", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        lastLoadThreadNameForTest = Thread.currentThread().name
        val login = decrypt(prefs.getString(KEY_LOGIN, null)) ?: return null
        val password = decrypt(prefs.getString(KEY_PASSWORD, null)) ?: return null
        return Credentials(login, password)
    }

    /**
     * Zoals [load], maar van een achtergronddraad: het aanmaken van de Keystore-sleutel
     * (eerste keer: TEE/StrongBox) en de cipher-bewerkingen lopen anders op de UI-draad,
     * net als bij de netwerkoproepen in [be.on5gs.eme.chat.Hb9qClient] en de cq-package.
     */
    suspend fun loadAsync(): Credentials? = withContext(Dispatchers.IO) { load() }

    fun save(login: String, password: String) {
        prefs.edit()
            .putString(KEY_LOGIN, encrypt(login))
            .putString(KEY_PASSWORD, encrypt(password))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    // ---------- AES-GCM met een sleutel uit de Android Keystore ----------

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String?): String? {
        if (stored == null) return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "eme_hb9q_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_LOGIN = "hb9q_login"
        private const val KEY_PASSWORD = "hb9q_password"

        /** Alleen voor tests: naam van de draad die [load] het laatst uitvoerde. */
        internal var lastLoadThreadNameForTest: String? = null
    }
}
