package app.schnarchwaechter.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.schnarchwaechter.SchnarchApp
import app.schnarchwaechter.core.BreathingPause
import app.schnarchwaechter.core.ClipRequest
import app.schnarchwaechter.core.MinuteStats
import app.schnarchwaechter.core.NightSession
import app.schnarchwaechter.core.SessionSink
import app.schnarchwaechter.core.SnoreEpisode
import app.schnarchwaechter.util.BatteryInfo
import app.schnarchwaechter.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Foreground service (type "microphone") that measures the night with the screen off.
 *
 * Power: the only regular work is one blocking AudioRecord.read() every 2 s plus cheap math. The
 * database is touched every 10 minutes, the notification is never updated, nothing else runs.
 */
class NightService : Service() {

    sealed interface State {
        data object Idle : State
        data object Starting : State
        data class Running(val nightId: Long, val startMs: Long, val sampleRate: Int) : State
        data object Finishing : State
        data class Finished(val nightId: Long) : State
        data class Failed(val message: String) : State
    }

    companion object {
        private const val TAG = "NightService"
        private const val ACTION_START = "app.schnarchwaechter.START"
        private const val ACTION_STOP = "app.schnarchwaechter.STOP"
        private const val BLOCK_SECONDS = 2

        private val mutableState = MutableStateFlow<State>(State.Idle)
        val state: StateFlow<State> = mutableState.asStateFlow()

        fun start(context: Context) {
            mutableState.value = State.Starting
            try {
                ContextCompat.startForegroundService(context, Intent(context, NightService::class.java).setAction(ACTION_START))
            } catch (e: Exception) {
                Log.e(TAG, "cannot start service", e)
                mutableState.value = State.Failed("Die Messung konnte nicht gestartet werden.")
            }
        }

        fun stopIntent(context: Context): Intent = Intent(context, NightService::class.java).setAction(ACTION_STOP)

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        /** The UI has shown the result: back to idle. */
        fun acknowledge() {
            val s = mutableState.value
            if (s is State.Finished || s is State.Failed) mutableState.value = State.Idle
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private val control = Mutex()
    private val pendingWrites = Collections.synchronizedList(mutableListOf<Job>())

    private var capture: CaptureThread? = null
    private var session: NightSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var nightId = 0L
    private var cpuAtStartMs = 0L

    private val app get() = application as SchnarchApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startNight()
            ACTION_STOP -> scope.launch { control.withLock { stopNight() } }
            else -> if (session == null) stopSelf()
        }
        // Not sticky: Android may not restart a microphone service from the background anyway.
        return START_NOT_STICKY
    }

    private fun startNight() {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, Notifications.NIGHT_ID, Notifications.night(this), type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            fail("Die Messung konnte nicht gestartet werden. Ist das Mikrofon erlaubt?")
            return
        }
        scope.launch {
            control.withLock {
                if (session != null) return@withLock
                if (!Permissions.hasMicrophone(this@NightService)) {
                    fail("Mikrofon-Berechtigung fehlt.")
                    return@withLock
                }
                val opened = AudioCapture.open()
                if (opened == null) {
                    fail("Das Mikrofon konnte nicht geöffnet werden. Nutzt eine andere App es gerade?")
                    return@withLock
                }
                val settings = app.settings.current
                val repo = app.repository
                runCatching { repo.cleanupOldClips(settings.clipRetentionDays) }
                val rate = opened.sampleRate
                val startMs = System.currentTimeMillis()
                nightId = repo.startNight(startMs, rate, settings, BatteryInfo.read(this@NightService))
                cpuAtStartMs = Process.getElapsedCpuTime()
                if (settings.extraWakeLock) acquireWakeLock()

                val sink = DbSink(nightId)
                val s = NightSession(startMs, rate, settings.detectionConfig(), settings.clipConfig(), sink)
                sink.session = s
                session = s
                capture = CaptureThread(opened, rate * BLOCK_SECONDS) { buf, n -> s.process(buf, n) }.also { it.start() }
                mutableState.value = State.Running(nightId, startMs, rate)
                Log.i(TAG, "night $nightId started at $rate Hz")
            }
        }
    }

    private suspend fun stopNight() {
        val s = session
        if (s == null) {
            withContext(Dispatchers.Main) { shutdownService() }
            if (mutableState.value !is State.Finished) mutableState.value = State.Idle
            return
        }
        mutableState.value = State.Finishing
        capture?.shutdown()
        capture = null
        s.finish()
        synchronized(pendingWrites) { pendingWrites.toList() }.joinAll() // also waits for clips still being compressed
        val cpuMs = Process.getElapsedCpuTime() - cpuAtStartMs
        val night = app.repository.finishNight(nightId, System.currentTimeMillis(), BatteryInfo.read(this), cpuMs)
        session = null
        releaseWakeLock()
        if (app.settings.current.morningNotification) Notifications.showSummary(this, night)
        mutableState.value = State.Finished(nightId)
        withContext(Dispatchers.Main) { shutdownService() }
    }

    private fun fail(message: String) {
        mutableState.value = State.Failed(message)
        releaseWakeLock()
        shutdownService()
    }

    private fun shutdownService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * While audio is being recorded, Android's audio system keeps the CPU partly awake anyway. This
     * extra partial wake lock (screen stays off!) only guarantees our 2-second processing on phones
     * with aggressive power saving. It has a safety timeout and is always released at the end.
     */
    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Schnarchwaechter:night").apply {
            setReferenceCounted(false)
            acquire(14 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        // Normal end: everything was saved in stopNight(). If the system kills us instead, the night
        // is closed on the next app start (marked as interrupted).
        capture?.shutdown()
        capture = null
        releaseWakeLock()
        scope.cancel()
        if (mutableState.value is State.Running || mutableState.value is State.Starting) mutableState.value = State.Idle
        super.onDestroy()
    }

    /** Writes the session results in order on one background thread. */
    private inner class DbSink(private val nightId: Long) : SessionSink {
        lateinit var session: NightSession

        private fun write(block: suspend () -> Unit) {
            val job = scope.launch(writer, start = CoroutineStart.LAZY) {
                try {
                    block()
                } catch (e: Exception) {
                    Log.w(TAG, "write failed", e)
                }
            }
            pendingWrites += job
            job.invokeOnCompletion { pendingWrites.remove(job) }
            job.start()
        }

        override fun saveMinutes(minutes: List<MinuteStats>) = write { app.repository.saveMinutes(nightId, minutes) }

        override fun saveEpisode(episode: SnoreEpisode) = write { app.repository.saveEpisode(nightId, episode) }

        override fun savePause(pause: BreathingPause) = write { app.repository.savePause(nightId, pause) }

        override fun saveClip(request: ClipRequest) = write {
            val id = runCatching { app.repository.saveClip(nightId, request) }.getOrNull()
            session.onClipResult(request, id)
        }
    }
}
