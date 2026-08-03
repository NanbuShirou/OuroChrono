package tw.nanbu.ourochrono

import android.util.Base64
import org.json.JSONObject

object JwtClaimsParser {
    data class Claims(
        val accountId: String?,
        val email: String?,
        val planType: String?,
        val expiresAtEpochSeconds: Long?
    )

    fun parse(token: String): Claims {
        val payload = parsePayload(token) ?: return Claims(null, null, null, null)
        val auth = payload.optJSONObject(AUTH_CLAIM)

        val accountId = firstNonBlank(
            auth?.optNullableString("chatgpt_account_id"),
            payload.optNullableString("chatgpt_account_id"),
            auth?.optNullableString("account_id")
        )
        val email = firstNonBlank(
            payload.optNullableString("email"),
            auth?.optNullableString("email")
        )
        val planType = firstNonBlank(
            auth?.optNullableString("chatgpt_plan_type"),
            payload.optNullableString("chatgpt_plan_type")
        )
        val expiresAt = payload.optNullableLong("exp")

        return Claims(accountId, email, planType, expiresAt)
    }

    private fun parsePayload(token: String): JSONObject? {
        val parts = token.split('.')
        if (parts.size < 2 || parts[1].isBlank()) return null
        return try {
            val bytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private const val AUTH_CLAIM = "https://api.openai.com/auth"
}
