package tw.nanbu.ourochrono

import android.content.Context
import org.json.JSONObject

object UsageCache {
    private const val PREFS_NAME = "ourochrono_usage_cache"
    private const val KEY_USAGE = "usage_json"
    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val CACHE_SCHEMA_VERSION = 2

    fun save(context: Context, snapshot: UsageSnapshot) {
        prefs(context).edit()
            .putInt(KEY_SCHEMA_VERSION, CACHE_SCHEMA_VERSION)
            .putString(KEY_USAGE, snapshot.toJson().toString())
            .apply()
    }

    fun saveSuccessful(context: Context, snapshot: UsageSnapshot) {
        val previous = load(context)
        save(context, snapshot)
        UsageRecoveryNotifier.notifyIfRecovered(context, previous, snapshot)
    }

    fun load(context: Context): UsageSnapshot? {
        val preferences = prefs(context)
        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) != CACHE_SCHEMA_VERSION) {
            return null
        }

        val raw = preferences.getString(KEY_USAGE, null) ?: return null
        return try {
            UsageSnapshot.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun markStale(context: Context, error: String) {
        val current = load(context)
        val stale = if (current != null) {
            current.copy(stale = true, error = error)
        } else {
            UsageSnapshot(
                windows = emptyList(),
                planType = null,
                resetCredits = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
                stale = true,
                error = error
            )
        }
        save(context, stale)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
