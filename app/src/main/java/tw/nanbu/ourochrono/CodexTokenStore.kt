package tw.nanbu.ourochrono

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CodexTokenStore {
    private const val PREFS_NAME = "ourochrono_oauth_session"
    private const val KEY_TOKEN_SET = "oauth_token_set"
    private const val KEYSTORE_ALIAS = "ourochrono_oauth_tokens_v1"
    private const val LEGACY_PREFS_NAME = "ourochrono_session"

    fun save(context: Context, tokens: OAuthTokenSet) {
        val encrypted = encrypt(tokens.toJson().toString())
        prefs(context).edit().putString(KEY_TOKEN_SET, encrypted).commit()
    }

    fun load(context: Context): OAuthTokenSet? {
        val encrypted = prefs(context).getString(KEY_TOKEN_SET, null) ?: return null
        val raw = decrypt(encrypted) ?: return null
        return try {
            OAuthTokenSet.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun hasTokens(context: Context): Boolean = load(context) != null

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    fun removeLegacyRelaySettings(context: Context) {
        context.applicationContext
            .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? {
        return try {
            val decoded = Base64.decode(value, Base64.NO_WRAP)
            if (decoded.size <= IV_SIZE) return null
            val iv = decoded.copyOfRange(0, IV_SIZE)
            val encrypted = decoded.copyOfRange(IV_SIZE, decoded.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private const val IV_SIZE = 12
}
