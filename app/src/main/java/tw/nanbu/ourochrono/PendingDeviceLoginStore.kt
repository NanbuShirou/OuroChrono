package tw.nanbu.ourochrono

import android.content.Context
import org.json.JSONObject

/**
 * Saves the short-lived device-code login session so Android can recreate
 * MainActivity while the browser is open without losing the pending login.
 */
object PendingDeviceLoginStore {
    private const val PREFS_NAME = "ourochrono_pending_device_login"
    private const val KEY_SESSION = "session"

    fun save(context: Context, session: DeviceCodeLoginSession) {
        val json = JSONObject().apply {
            put("verificationUrl", session.verificationUrl)
            put("userCode", session.userCode)
            put("deviceAuthId", session.deviceAuthId)
            put("intervalSeconds", session.intervalSeconds)
            put("createdAtEpochMillis", session.createdAtEpochMillis)
        }
        prefs(context).edit().putString(KEY_SESSION, json.toString()).commit()
    }

    fun load(context: Context): DeviceCodeLoginSession? {
        val raw = prefs(context).getString(KEY_SESSION, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val verificationUrl = json.optString("verificationUrl")
            val userCode = json.optString("userCode")
            val deviceAuthId = json.optString("deviceAuthId")
            if (verificationUrl.isBlank() || userCode.isBlank() || deviceAuthId.isBlank()) {
                clear(context)
                null
            } else {
                DeviceCodeLoginSession(
                    verificationUrl = verificationUrl,
                    userCode = userCode,
                    deviceAuthId = deviceAuthId,
                    intervalSeconds = json.optLong("intervalSeconds", 5L).coerceIn(1L, 30L),
                    createdAtEpochMillis = json.optLong(
                        "createdAtEpochMillis",
                        System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
