package tw.nanbu.ourochrono

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException

class RefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val scheduled = inputData.getBoolean(RefreshScheduler.INPUT_IS_SCHEDULED, false)
        val generation = inputData.getInt(
            RefreshScheduler.INPUT_SCHEDULE_GENERATION,
            AppPreferences.scheduleGeneration(applicationContext)
        )

        if (scheduled && !RefreshScheduler.onScheduledWorkStarted(applicationContext, generation)) {
            return Result.success()
        }

        val result = runRefresh(scheduled)
        if (scheduled) {
            RefreshScheduler.scheduleNextAfterRun(applicationContext, generation)
        }
        return result
    }

    private fun runRefresh(scheduled: Boolean): Result {
        if (!CodexTokenStore.hasTokens(applicationContext)) {
            UsageCache.markStale(applicationContext, "尚未登入 ChatGPT")
            OuroChronoWidget.updateAll(applicationContext)
            return Result.success()
        }

        return try {
            val usage = CodexUsageClient.getUsage(applicationContext)
            UsageCache.saveSuccessful(applicationContext, usage)
            OuroChronoWidget.updateAll(applicationContext)
            Result.success()
        } catch (error: CodexAuthException) {
            UsageCache.markStale(applicationContext, error.message ?: "登入已失效")
            OuroChronoWidget.updateAll(applicationContext)
            Result.success()
        } catch (error: CodexHttpException) {
            val message = when (error.statusCode) {
                401, 403 -> "Codex 登入已失效"
                429 -> "更新過於頻繁"
                else -> error.message ?: "更新失敗"
            }
            UsageCache.markStale(applicationContext, message)
            OuroChronoWidget.updateAll(applicationContext)
            if (!scheduled && (error.statusCode >= 500 || error.statusCode == 429)) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (error: IOException) {
            UsageCache.markStale(applicationContext, error.message ?: "網路連線失敗")
            OuroChronoWidget.updateAll(applicationContext)
            if (scheduled) Result.success() else Result.retry()
        } catch (error: Exception) {
            UsageCache.markStale(applicationContext, error.message ?: "資料格式錯誤")
            OuroChronoWidget.updateAll(applicationContext)
            if (scheduled) Result.success() else Result.failure()
        }
    }
}
