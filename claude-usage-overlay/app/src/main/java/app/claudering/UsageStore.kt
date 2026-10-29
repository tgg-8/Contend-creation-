package app.claudering

import android.content.Context

/** Der zuletzt eingetragene Stand aus den Claude-Einstellungen. */
data class Usage(
    val percentLeft: Int,
    val resetAt: Long,
    val savedAt: Long,
) {
    fun isReset(now: Long) = now >= resetAt

    /** Nach dem Reset zeigen wir wieder 100 %, bis neue Werte eingetragen werden. */
    fun displayPercent(now: Long) = if (isReset(now)) 100 else percentLeft
}

/** Speichert alles nur lokal auf dem Handy (SharedPreferences). Nichts wird gesendet. */
object UsageStore {
    private const val FILE = "claude_ring"
    private const val KEY_PERCENT = "percent_left"
    private const val KEY_RESET = "reset_at"
    private const val KEY_SAVED = "saved_at"
    private const val KEY_X = "ring_x"
    private const val KEY_Y = "ring_y"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): Usage? {
        val p = prefs(context)
        if (!p.contains(KEY_PERCENT)) return null
        return Usage(
            percentLeft = p.getInt(KEY_PERCENT, 100),
            resetAt = p.getLong(KEY_RESET, 0L),
            savedAt = p.getLong(KEY_SAVED, 0L),
        )
    }

    fun save(context: Context, percentLeft: Int, resetAt: Long) {
        prefs(context).edit()
            .putInt(KEY_PERCENT, percentLeft)
            .putLong(KEY_RESET, resetAt)
            .putLong(KEY_SAVED, System.currentTimeMillis())
            .apply()
    }

    fun loadPosition(context: Context): Pair<Int, Int>? {
        val p = prefs(context)
        if (!p.contains(KEY_X)) return null
        return p.getInt(KEY_X, 0) to p.getInt(KEY_Y, 0)
    }

    fun savePosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }
}
