package app.schnarchwaechter

import android.app.Application
import app.schnarchwaechter.data.AppDatabase
import app.schnarchwaechter.data.NightRepository
import app.schnarchwaechter.data.SettingsStore
import app.schnarchwaechter.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SchnarchApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: NightRepository
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val processStartMs = System.currentTimeMillis()
        settings = SettingsStore(this)
        repository = NightRepository(this, AppDatabase.create(this))
        Notifications.createChannels(this)
        appScope.launch {
            // A night that is still "open" from before this process started was cut off by the system.
            repository.recoverInterruptedNights(startedBeforeMs = processStartMs)
            repository.cleanupOldClips(settings.current.clipRetentionDays)
        }
    }
}
