package tw.nanbu.ourochrono

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

data class OAuthTokenSet(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAtEpochSeconds: Long,
    val accountId: String,
    val email: String?,
    val planType: String?
) {
    fun toAccountInfo(): AccountInfo {
        return AccountInfo(
            authenticated = true,
            email = email,
            planType = planType,
            authMode = "chatgpt"
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("accessToken", accessToken)
            put("refreshToken", refreshToken)
            put("idToken", idToken)
            put("expiresAt", expiresAtEpochSeconds)
            put("accountId", accountId)
            put("email", email ?: JSONObject.NULL)
            put("planType", planType ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): OAuthTokenSet {
            return OAuthTokenSet(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                idToken = json.optString("idToken"),
                expiresAtEpochSeconds = json.optLong("expiresAt", 0L),
                accountId = json.getString("accountId"),
                email = json.optNullableString("email"),
                planType = json.optNullableString("planType")
            )
        }
    }
}

data class DeviceCodeLoginSession(
    val verificationUrl: String,
    val userCode: String,
    val deviceAuthId: String,
    val intervalSeconds: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val cancelled: AtomicBoolean = AtomicBoolean(false)
) {
    fun cancel() {
        cancelled.set(true)
    }
}

data class DeviceCodeAuthorization(
    val authorizationCode: String,
    val codeVerifier: String,
    val codeChallenge: String
)
