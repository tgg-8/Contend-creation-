package app.schnarchwaechter.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NightDao {
    @Insert
    suspend fun insertNight(night: NightEntity): Long

    @Update
    suspend fun updateNight(night: NightEntity)

    @Query("SELECT * FROM nights WHERE id = :id")
    suspend fun night(id: Long): NightEntity?

    @Query("SELECT * FROM nights WHERE id = :id")
    fun observeNight(id: Long): Flow<NightEntity?>

    @Query("SELECT * FROM nights WHERE endMs IS NOT NULL ORDER BY startMs DESC")
    fun observeFinishedNights(): Flow<List<NightEntity>>

    @Query("SELECT * FROM nights WHERE endMs IS NOT NULL ORDER BY startMs DESC")
    suspend fun finishedNights(): List<NightEntity>

    @Query("SELECT * FROM nights WHERE endMs IS NULL")
    suspend fun openNights(): List<NightEntity>

    @Query("DELETE FROM nights WHERE id = :id")
    suspend fun deleteNight(id: Long)

    @Query("DELETE FROM nights")
    suspend fun deleteAllNights()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMinutes(minutes: List<MinuteEntity>)

    @Query("SELECT * FROM minutes WHERE nightId = :nightId ORDER BY startMs")
    suspend fun minutes(nightId: Long): List<MinuteEntity>

    @Query("SELECT * FROM minutes ORDER BY startMs")
    suspend fun allMinutes(): List<MinuteEntity>

    @Query("SELECT MAX(startMs) FROM minutes WHERE nightId = :nightId")
    suspend fun lastMinuteStart(nightId: Long): Long?

    @Insert
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Query("SELECT * FROM episodes WHERE nightId = :nightId ORDER BY startMs")
    suspend fun episodes(nightId: Long): List<EpisodeEntity>

    @Insert
    suspend fun insertPause(pause: PauseEntity): Long

    @Query("SELECT * FROM pauses WHERE nightId = :nightId ORDER BY startMs")
    suspend fun pauses(nightId: Long): List<PauseEntity>

    @Insert
    suspend fun insertClip(clip: ClipEntity): Long

    @Query("SELECT * FROM clips WHERE id = :id")
    suspend fun clip(id: Long): ClipEntity?

    @Query("SELECT * FROM clips WHERE nightId = :nightId ORDER BY triggerMs")
    fun observeClips(nightId: Long): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE nightId = :nightId")
    suspend fun clips(nightId: Long): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE triggerMs < :beforeMs")
    suspend fun clipsOlderThan(beforeMs: Long): List<ClipEntity>

    @Query("SELECT * FROM clips")
    suspend fun allClips(): List<ClipEntity>

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteClip(id: Long)

    @Query("UPDATE nights SET clipCount = (SELECT COUNT(*) FROM clips WHERE clips.nightId = :nightId) WHERE id = :nightId")
    suspend fun refreshClipCount(nightId: Long)
}

@Database(
    entities = [NightEntity::class, MinuteEntity::class, EpisodeEntity::class, PauseEntity::class, ClipEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schnarch.db").build()
    }
}
