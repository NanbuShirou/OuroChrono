package tw.nanbu.ourochrono

import android.content.Context
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Owns the long-running device-code polling outside MainActivity.
 *
 * Opening a browser can destroy/recreate the Activity. The login must therefore
 * not be cancelled from Activity.onDestroy(), otherwise the web authorization
 * succeeds while the app silently throws away the pending session.
 */
object DeviceLoginCoordinator {
    sealed class State {
        data object Idle : State()
        data class Waiting(
            val session: DeviceCodeLoginSession,
            val elapsedSeconds: Long,
            val statusMessage: String? = null
        ) : State()
        data class Succeeded(val tokens: OAuthTokenSet) : State()
        data class Failed(
            val session: DeviceCodeLoginSession?,
            val message: String,
            val canRetry: Boolean
        ) : State()
        data object Cancelled : State()
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ourochrono-device-login").apply { isDaemon = true }
    }
    private val listeners = CopyOnWriteArraySet<(State) -> Unit>()
    private val lock = Any()

    @Volatile
    private var state: State = State.Idle

    @Volatile
    private var activeSession: DeviceCodeLoginSession? = null

    private var pollingFuture: Future<*>? = null

    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners -= listener
    }

    fun currentSession(): DeviceCodeLoginSession? = activeSession

    fun ensurePolling(context: Context, session: DeviceCodeLoginSession) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val existing = pollingFuture
            if (
                existing != null && !existing.isDone &&
                activeSession?.deviceAuthId == session.deviceAuthId
            ) {
                return
            }

            activeSession = session
            updateState(
                State.Waiting(
                    session,
                    ((System.currentTimeMillis() - session.createdAtEpochMillis) / 1000L)
                        .coerceAtLeast(0L)
                )
            )

            pollingFuture = executor.submit {
                try {
                    val tokens = CodexOAuthClient.completeDeviceCodeLogin(
                        appContext,
                        session
                    ) { elapsedSeconds, statusMessage ->
                        updateState(State.Waiting(session, elapsedSeconds, statusMessage))
                    }

                    PendingDeviceLoginStore.clear(appContext)
                    synchronized(lock) {
                        activeSession = null
                        pollingFuture = null
                    }
                    updateState(State.Succeeded(tokens))
                } catch (_: CancellationException) {
                    val explicitlyCancelled = session.cancelled.get()
                    synchronized(lock) {
                        pollingFuture = null
                        if (explicitlyCancelled) activeSession = null
                    }
                    if (explicitlyCancelled) {
                        PendingDeviceLoginStore.clear(appContext)
                        updateState(State.Cancelled)
                    } else {
                        // A lifecycle or transient interruption must not erase the
                        // persisted device-code session. The next Activity can retry.
                        updateState(
                            State.Failed(
                                session,
                                "登入檢查被中斷，請按「重新檢查登入結果」。",
                                canRetry = true
                            )
                        )
                    }
                } catch (error: Exception) {
                    val expired = CodexOAuthClient.isDeviceLoginExpired(session)
                    synchronized(lock) {
                        pollingFuture = null
                        if (expired) activeSession = null
                    }
                    if (expired) PendingDeviceLoginStore.clear(appContext)
                    updateState(
                        State.Failed(
                            session = if (expired) null else session,
                            message = error.message ?: "登入失敗",
                            canRetry = !expired
                        )
                    )
                }
            }
        }
    }

    fun retry(context: Context): Boolean {
        val session = activeSession ?: PendingDeviceLoginStore.load(context) ?: return false
        if (CodexOAuthClient.isDeviceLoginExpired(session)) {
            PendingDeviceLoginStore.clear(context)
            synchronized(lock) {
                activeSession = null
                pollingFuture = null
            }
            updateState(
                State.Failed(
                    session = null,
                    message = "登入代碼已逾時，請重新產生一次性代碼。",
                    canRetry = false
                )
            )
            return false
        }
        ensurePolling(context, session)
        return true
    }

    fun cancel(context: Context, notify: Boolean = true) {
        synchronized(lock) {
            activeSession?.cancel()
            pollingFuture?.cancel(true)
            pollingFuture = null
            activeSession = null
        }
        PendingDeviceLoginStore.clear(context)
        if (notify) {
            updateState(State.Cancelled)
        } else {
            state = State.Idle
        }
    }

    fun resetState() {
        synchronized(lock) {
            if (pollingFuture?.isDone != false) {
                state = State.Idle
                activeSession = null
                pollingFuture = null
            }
        }
    }

    private fun updateState(newState: State) {
        state = newState
        listeners.forEach { listener ->
            runCatching { listener(newState) }
        }
    }
}
