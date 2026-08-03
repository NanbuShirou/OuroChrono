package tw.nanbu.ourochrono

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

class CodexAuthException(
    message: String,
    val errorCode: String? = null,
    cause: Throwable? = null
) : IOException(message, cause)

object CodexOAuthClient {
    private val refreshLock = Any()

    fun isDeviceLoginExpired(session: DeviceCodeLoginSession): Boolean {
        return System.currentTimeMillis() >=
            session.createdAtEpochMillis + DEVICE_LOGIN_TIMEOUT_MILLIS
    }

    fun requestDeviceCode(): DeviceCodeLoginSession {
        val response = postJson(
            DEVICE_USER_CODE_ENDPOINT,
            JSONObject().apply { put("client_id", CLIENT_ID) }
        )

        val deviceAuthId = response.optString("device_auth_id").takeIf { it.isNotBlank() }
            ?: throw CodexAuthException("OpenAI 回應缺少 device_auth_id", "missing_device_auth_id")
        val userCode = firstNonBlank(
            response.optString("user_code"),
            response.optString("usercode")
        ) ?: throw CodexAuthException("OpenAI 回應缺少登入代碼", "missing_user_code")
        val interval = parseInterval(response.opt("interval")).coerceIn(1L, 30L)

        return DeviceCodeLoginSession(
            verificationUrl = DEVICE_VERIFICATION_URL,
            userCode = userCode,
            deviceAuthId = deviceAuthId,
            intervalSeconds = interval
        )
    }

    fun completeDeviceCodeLogin(
        context: Context,
        session: DeviceCodeLoginSession,
        onWaiting: ((elapsedSeconds: Long, statusMessage: String?) -> Unit)? = null
    ): OAuthTokenSet {
        val authorization = pollForDeviceAuthorization(session, onWaiting)
        val response = try {
            retryTransientNetwork(
                deadlineEpochMillis = System.currentTimeMillis() + TOKEN_EXCHANGE_RETRY_MILLIS,
                onRetry = { message ->
                    val elapsed = (System.currentTimeMillis() - session.createdAtEpochMillis) / 1000
                    onWaiting?.invoke(
                        elapsed,
                        "網頁授權已完成，但模擬器網路解析暫時失敗，正在重試 Token 交換：$message"
                    )
                }
            ) {
                postForm(
                    TOKEN_ENDPOINT,
                    linkedMapOf(
                        "grant_type" to "authorization_code",
                        "code" to authorization.authorizationCode,
                        "redirect_uri" to DEVICE_REDIRECT_URI,
                        "client_id" to CLIENT_ID,
                        "code_verifier" to authorization.codeVerifier
                    )
                )
            }
        } catch (error: CodexAuthException) {
            throw CodexAuthException(
                "網頁授權已完成，但 OAuth Token 交換失敗：${error.message}",
                error.errorCode,
                error
            )
        }
        val tokens = parseTokenResponse(response, previous = null)
        CodexTokenStore.save(context, tokens)
        return tokens
    }

    fun getValidTokens(context: Context, forceRefresh: Boolean = false): OAuthTokenSet {
        synchronized(refreshLock) {
            val current = CodexTokenStore.load(context)
                ?: throw CodexAuthException("尚未登入 ChatGPT", "not_authenticated")
            val now = System.currentTimeMillis() / 1000
            if (!forceRefresh && current.expiresAtEpochSeconds > now + REFRESH_THRESHOLD_SECONDS) {
                return current
            }
            return refreshTokensLocked(context, current)
        }
    }

    fun logout(context: Context) {
        val current = CodexTokenStore.load(context)
        if (current != null && current.refreshToken.isNotBlank()) {
            runCatching {
                postJson(
                    REVOKE_ENDPOINT,
                    JSONObject().apply {
                        put("token", current.refreshToken)
                        put("token_type_hint", "refresh_token")
                        put("client_id", CLIENT_ID)
                    }
                )
            }
        }
        CodexTokenStore.clear(context)
    }

    private fun pollForDeviceAuthorization(
        session: DeviceCodeLoginSession,
        onWaiting: ((elapsedSeconds: Long, statusMessage: String?) -> Unit)?
    ): DeviceCodeAuthorization {
        val deadline = session.createdAtEpochMillis + DEVICE_LOGIN_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (session.cancelled.get() || Thread.currentThread().isInterrupted) {
                throw CancellationException("登入已取消")
            }

            val raw = try {
                requestRaw(
                    DEVICE_TOKEN_ENDPOINT,
                    "POST",
                    "application/json",
                    JSONObject().apply {
                        put("device_auth_id", session.deviceAuthId)
                        put("user_code", session.userCode)
                    }.toString()
                )
            } catch (error: CodexAuthException) {
                if (!isTransientNetworkError(error)) throw error

                val elapsed = (System.currentTimeMillis() - session.createdAtEpochMillis) / 1000
                onWaiting?.invoke(
                    elapsed,
                    "模擬器的 App 網路解析暫時失敗，正在自動重試：${networkErrorLabel(error)}"
                )
                sleepPollingInterval(session)
                continue
            }

            if (raw.statusCode in 200..299) {
                val json = if (raw.body.isBlank()) JSONObject() else JSONObject(raw.body)
                return DeviceCodeAuthorization(
                    authorizationCode = json.optString("authorization_code")
                        .takeIf { it.isNotBlank() }
                        ?: throw CodexAuthException(
                            "OpenAI 回應缺少 authorization_code",
                            "missing_authorization_code"
                        ),
                    codeVerifier = json.optString("code_verifier")
                        .takeIf { it.isNotBlank() }
                        ?: throw CodexAuthException(
                            "OpenAI 回應缺少 code_verifier",
                            "missing_code_verifier"
                        ),
                    codeChallenge = json.optString("code_challenge")
                )
            }

            if (raw.statusCode != 403 && raw.statusCode != 404) {
                throw parseAuthError(raw.statusCode, raw.body)
            }

            val lowerBody = raw.body.lowercase()
            if (
                lowerBody.contains("device code authorization") &&
                (lowerBody.contains("enable") || lowerBody.contains("disabled"))
            ) {
                throw CodexAuthException(
                    "請先在 ChatGPT 的設定 > 安全性中開啟 Codex 裝置代碼授權，然後重新產生一次性代碼。",
                    "device_code_authorization_disabled"
                )
            }

            val elapsed = (System.currentTimeMillis() - session.createdAtEpochMillis) / 1000
            onWaiting?.invoke(elapsed, null)
            sleepPollingInterval(session)
        }

        throw CodexAuthException(
            "登入逾時。請重新產生代碼；若網頁提示未啟用 Device Code，請先到 ChatGPT 安全性設定開啟 Codex 裝置代碼授權。",
            "device_auth_timeout"
        )
    }

    private fun refreshTokensLocked(context: Context, current: OAuthTokenSet): OAuthTokenSet {
        if (current.refreshToken.isBlank()) {
            CodexTokenStore.clear(context)
            throw CodexAuthException("登入憑證無法更新，請重新登入", "missing_refresh_token")
        }

        val response = try {
            postForm(
                TOKEN_ENDPOINT,
                linkedMapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to current.refreshToken,
                    "client_id" to CLIENT_ID
                )
            )
        } catch (error: CodexAuthException) {
            if (error.errorCode == "invalid_grant" || error.errorCode == "token_expired") {
                CodexTokenStore.clear(context)
            }
            throw error
        }

        val refreshed = parseTokenResponse(response, current)
        CodexTokenStore.save(context, refreshed)
        return refreshed
    }

    private fun parseTokenResponse(response: JSONObject, previous: OAuthTokenSet?): OAuthTokenSet {
        val accessToken = response.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw CodexAuthException("OpenAI 回應缺少 access_token", "missing_access_token")
        val refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: previous?.refreshToken
            ?: throw CodexAuthException("OpenAI 回應缺少 refresh_token", "missing_refresh_token")
        val idToken = response.optString("id_token").takeIf { it.isNotBlank() }
            ?: previous?.idToken.orEmpty()

        val accessClaims = JwtClaimsParser.parse(accessToken)
        val idClaims = JwtClaimsParser.parse(idToken)
        val accountId = firstNonBlank(
            idClaims.accountId,
            accessClaims.accountId,
            previous?.accountId
        ) ?: throw CodexAuthException(
            "登入成功，但 Token 中沒有 ChatGPT 帳號識別碼",
            "missing_account_id"
        )
        val expiresIn = response.optLong("expires_in", 0L)
        val now = System.currentTimeMillis() / 1000
        val expiresAt = when {
            expiresIn > 0 -> now + expiresIn
            accessClaims.expiresAtEpochSeconds != null -> accessClaims.expiresAtEpochSeconds
            else -> now + DEFAULT_TOKEN_LIFETIME_SECONDS
        }

        return OAuthTokenSet(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken,
            expiresAtEpochSeconds = expiresAt,
            accountId = accountId,
            email = firstNonBlank(idClaims.email, accessClaims.email, previous?.email),
            planType = firstNonBlank(idClaims.planType, accessClaims.planType, previous?.planType)
        )
    }

    private fun postForm(url: String, values: Map<String, String>): JSONObject {
        val body = values.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return requestJson(url, "POST", "application/x-www-form-urlencoded", body)
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        return requestJson(url, "POST", "application/json", body.toString())
    }

    private fun requestJson(
        url: String,
        method: String,
        contentType: String,
        body: String
    ): JSONObject {
        val raw = requestRaw(url, method, contentType, body)
        if (raw.statusCode !in 200..299) {
            throw parseAuthError(raw.statusCode, raw.body)
        }
        return if (raw.body.isBlank()) JSONObject() else JSONObject(raw.body)
    }

    private fun requestRaw(
        url: String,
        method: String,
        contentType: String,
        body: String
    ): RawHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("User-Agent", "codex_cli_rs/0.2.1 (android)")
            connection.doOutput = true
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            RawHttpResponse(status, text)
        } catch (error: Exception) {
            throw CodexAuthException(error.message ?: "無法連線 OpenAI 登入服務", cause = error)
        } finally {
            connection.disconnect()
        }
    }

    private fun sleepPollingInterval(session: DeviceCodeLoginSession) {
        try {
            Thread.sleep(session.intervalSeconds * 1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("登入已取消")
        }
    }

    private fun <T> retryTransientNetwork(
        deadlineEpochMillis: Long,
        onRetry: (String) -> Unit,
        operation: () -> T
    ): T {
        var delayMillis = 1_500L
        var lastError: CodexAuthException? = null

        while (System.currentTimeMillis() < deadlineEpochMillis) {
            try {
                return operation()
            } catch (error: CodexAuthException) {
                if (!isTransientNetworkError(error)) throw error
                lastError = error
                onRetry(networkErrorLabel(error))
                try {
                    Thread.sleep(delayMillis)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw CancellationException("登入已取消")
                }
                delayMillis = (delayMillis * 2).coerceAtMost(8_000L)
            }
        }

        throw lastError ?: CodexAuthException("模擬器網路暫時無法連線 OpenAI")
    }

    private fun isTransientNetworkError(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            if (
                current is UnknownHostException ||
                current is SocketTimeoutException ||
                current is ConnectException ||
                current is NoRouteToHostException ||
                current is SocketException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun networkErrorLabel(error: Throwable): String {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is UnknownHostException -> return "DNS 無法解析 auth.openai.com"
                is SocketTimeoutException -> return "連線逾時"
                is ConnectException -> return "無法建立連線"
                is NoRouteToHostException -> return "找不到網路路徑"
                is SocketException -> return current.message ?: "Socket 連線中斷"
            }
            current = current.cause
        }
        return error.message ?: "暫時性網路錯誤"
    }

    private fun parseAuthError(status: Int, text: String): CodexAuthException {
        var code: String? = null
        var message: String? = null
        runCatching {
            val json = JSONObject(text)
            val error = json.opt("error")
            when (error) {
                is JSONObject -> {
                    code = error.optNullableString("code") ?: error.optNullableString("type")
                    message = error.optNullableString("message")
                }
                is String -> code = error
            }
            message = json.optNullableString("error_description") ?: message
            message = json.optNullableString("detail") ?: message
        }
        return CodexAuthException(
            message?.takeIf { it.isNotBlank() }
                ?: code?.let { "OpenAI 登入失敗：$it" }
                ?: "OpenAI 登入服務回傳 HTTP $status",
            code
        )
    }

    private fun parseInterval(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull() ?: DEFAULT_POLL_INTERVAL_SECONDS
            else -> DEFAULT_POLL_INTERVAL_SECONDS
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private data class RawHttpResponse(val statusCode: Int, val body: String)

    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val AUTH_BASE_URL = "https://auth.openai.com"
    private const val DEVICE_USER_CODE_ENDPOINT =
        "$AUTH_BASE_URL/api/accounts/deviceauth/usercode"
    private const val DEVICE_TOKEN_ENDPOINT =
        "$AUTH_BASE_URL/api/accounts/deviceauth/token"
    private const val DEVICE_VERIFICATION_URL = "$AUTH_BASE_URL/codex/device"
    private const val DEVICE_REDIRECT_URI = "$AUTH_BASE_URL/deviceauth/callback"
    private const val TOKEN_ENDPOINT = "$AUTH_BASE_URL/oauth/token"
    private const val REVOKE_ENDPOINT = "$AUTH_BASE_URL/oauth/revoke"
    private const val REFRESH_THRESHOLD_SECONDS = 300L
    private const val DEFAULT_TOKEN_LIFETIME_SECONDS = 3_600L
    private const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
    private const val DEVICE_LOGIN_TIMEOUT_MILLIS = 15L * 60L * 1000L
    private const val TOKEN_EXCHANGE_RETRY_MILLIS = 60L * 1000L
}
