package tw.nanbu.ourochrono

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

open class CodexApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

class CodexHttpException(
    val statusCode: Int,
    message: String
) : CodexApiException(message)

object CodexUsageClient {
    fun getUsage(context: Context): UsageSnapshot {
        var tokens = CodexOAuthClient.getValidTokens(context)
        var response = try {
            requestUsage(tokens)
        } catch (error: CodexHttpException) {
            if (error.statusCode != 401) throw error
            tokens = CodexOAuthClient.getValidTokens(context, forceRefresh = true)
            requestUsage(tokens)
        }

        val resetCredits = runCatching { requestResetCredits(tokens) }.getOrNull()
        return parseUsage(response, tokens, resetCredits)
    }

    private fun requestUsage(tokens: OAuthTokenSet): JSONObject {
        var lastError: CodexHttpException? = null
        for (endpoint in USAGE_ENDPOINTS) {
            try {
                return requestJson(endpoint, tokens)
            } catch (error: CodexHttpException) {
                lastError = error
                if (error.statusCode == 401) throw error
            }
        }
        throw lastError ?: CodexApiException("無法取得 Codex 用量")
    }

    private fun requestResetCredits(tokens: OAuthTokenSet): Int? {
        val json = requestJson(RESET_CREDITS_ENDPOINT, tokens)
        json.optNullableInt("available_count")?.let { return it }
        json.optJSONObject("rate_limit_reset_credits")
            ?.optNullableInt("available_count")
            ?.let { return it }

        val credits = json.optJSONArray("credits") ?: json.optJSONArray("data")
        if (credits != null) {
            var available = 0
            for (index in 0 until credits.length()) {
                val item = credits.optJSONObject(index) ?: continue
                val status = item.optString("status", "available")
                if (status.equals("available", ignoreCase = true)) available += 1
            }
            return available
        }
        return null
    }

    private fun requestJson(url: String, tokens: OAuthTokenSet): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${tokens.accessToken}")
            connection.setRequestProperty("ChatGPT-Account-ID", tokens.accountId)
            connection.setRequestProperty("originator", "codex_cli_rs")
            connection.setRequestProperty("OpenAI-Beta", "codex-1")
            connection.setRequestProperty("User-Agent", "codex_cli_rs/0.2.1 (android)")

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw CodexHttpException(status, parseError(status, text))
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (error: CodexHttpException) {
            throw error
        } catch (error: Exception) {
            throw CodexApiException(error.message ?: "無法連線 Codex 用量服務", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseUsage(
        json: JSONObject,
        tokens: OAuthTokenSet,
        resetCredits: Int?
    ): UsageSnapshot {
        val windows = mutableListOf<RateLimitWindow>()
        val rateLimit = json.optJSONObject("rate_limit")
        if (rateLimit != null) {
            UsageWindowParser.parse(
                rateLimit.optJSONObject("primary_window"),
                fallbackLimitId = "codex:primary",
                sourceSlot = "primary"
            )?.let(windows::add)
            UsageWindowParser.parse(
                rateLimit.optJSONObject("secondary_window"),
                fallbackLimitId = "codex:secondary",
                sourceSlot = "secondary"
            )?.let(windows::add)
        }

        val rateLimits = json.optJSONObject("rate_limits")
        if (windows.isEmpty() && rateLimits != null) {
            UsageWindowParser.parse(
                rateLimits.optJSONObject("primary"),
                fallbackLimitId = "codex:primary",
                sourceSlot = "primary"
            )?.let(windows::add)
            UsageWindowParser.parse(
                rateLimits.optJSONObject("secondary"),
                fallbackLimitId = "codex:secondary",
                sourceSlot = "secondary"
            )?.let(windows::add)
        }

        if (windows.isEmpty()) {
            UsageWindowParser.parse(
                json.optJSONObject("primary"),
                fallbackLimitId = "codex:primary",
                sourceSlot = "primary"
            )?.let(windows::add)
            UsageWindowParser.parse(
                json.optJSONObject("secondary"),
                fallbackLimitId = "codex:secondary",
                sourceSlot = "secondary"
            )?.let(windows::add)
        }

        if (windows.isEmpty()) {
            throw CodexApiException("Codex 用量回應中沒有可辨識的限制視窗")
        }

        val embeddedCredits = json.optJSONObject("rate_limit_reset_credits")
            ?.optNullableInt("available_count")
            ?: json.optJSONObject("credits")?.optNullableInt("available_count")

        return UsageSnapshot(
            windows = windows,
            planType = json.optNullableString("plan_type") ?: tokens.planType,
            resetCredits = resetCredits ?: embeddedCredits,
            updatedAtEpochMillis = System.currentTimeMillis(),
            stale = false,
            error = null
        )
    }

    private fun parseError(status: Int, text: String): String {
        if (text.isBlank()) return "Codex 用量服務回傳 HTTP $status"
        return try {
            val json = JSONObject(text)
            when (val error = json.opt("error")) {
                is JSONObject -> error.optString("message", "Codex 用量服務回傳 HTTP $status")
                is String -> error
                else -> json.optString("detail", "Codex 用量服務回傳 HTTP $status")
            }
        } catch (_: Exception) {
            "Codex 用量服務回傳 HTTP $status"
        }
    }

    private val USAGE_ENDPOINTS = listOf(
        "https://chatgpt.com/backend-api/wham/usage",
        "https://chatgpt.com/backend-api/codex/usage"
    )
    private const val RESET_CREDITS_ENDPOINT =
        "https://chatgpt.com/backend-api/wham/rate-limit-reset-credits"
}
