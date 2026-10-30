package app.schnarchwaechter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.schnarchwaechter.SchnarchApp
import app.schnarchwaechter.data.AppSettings
import app.schnarchwaechter.data.ClipEntity
import app.schnarchwaechter.data.NightDetail
import app.schnarchwaechter.data.NightEntity
import app.schnarchwaechter.service.AudioCapture
import app.schnarchwaechter.service.NightService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SchnarchApp
    private val repo = app.repository

    val settings: StateFlow<AppSettings> = app.settings.settings
    val serviceState: StateFlow<NightService.State> = NightService.state
    val nights: StateFlow<List<NightEntity>> =
        repo.dao.observeFinishedNights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateSettings(change: (AppSettings) -> AppSettings) = app.settings.update(change)

    suspend fun detail(nightId: Long): NightDetail? = withContext(Dispatchers.IO) { repo.loadDetail(nightId) }

    fun clips(nightId: Long): Flow<List<ClipEntity>> = repo.dao.observeClips(nightId)

    suspend fun clipStorageBytes(): Long = withContext(Dispatchers.IO) { repo.dao.allClips().sumOf { it.sizeBytes } }

    fun deleteClip(clip: ClipEntity) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteClip(clip) }
    }

    fun deleteNight(nightId: Long, done: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteNight(nightId) }
            done()
        }
    }

    fun deleteAll(done: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteAll() }
            done()
        }
    }

    fun applyRetention() {
        viewModelScope.launch(Dispatchers.IO) { repo.cleanupOldClips(settings.value.clipRetentionDays) }
    }

    fun exportCsv(uri: Uri, minutes: Boolean, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)!!.use { out ->
                        if (minutes) repo.exportMinutesCsv(out) else repo.exportNightsCsv(out)
                    }
                }.isSuccess
            }
            done(ok)
        }
    }

    /** 5 s level measurement for the optional calibration (dBFS), null if the microphone failed. */
    suspend fun measureLevel(): Double? = withContext(Dispatchers.IO) { AudioCapture.measureLevelDb(5) }
}
