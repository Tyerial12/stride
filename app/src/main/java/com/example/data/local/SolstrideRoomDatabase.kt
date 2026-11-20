package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending_step_batches")
data class PendingStepBatchEntity(
    @PrimaryKey val batchId: String,
    val userId: String,
    val source: String, // "PHONE" or "GALAXY_WATCH"
    val stepDelta: Long,
    val recordedAtMs: Long,
    val synced: Boolean = false
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val source: String,
    val stepDelta: Long,
    val summary: String,
    val timestampMs: Long
)

@Dao
interface OfflineSyncDao {
    @Query("SELECT * FROM pending_step_batches WHERE userId = :userId AND synced = 0 ORDER BY recordedAtMs ASC")
    fun observePendingBatches(userId: String): Flow<List<PendingStepBatchEntity>>

    @Query("SELECT * FROM pending_step_batches WHERE userId = :userId AND synced = 0 ORDER BY recordedAtMs ASC")
    suspend fun getPendingBatches(userId: String): List<PendingStepBatchEntity>

    @Query("SELECT * FROM pending_step_batches WHERE userId = '__WATCH_UNBOUND__' AND synced = 0 ORDER BY recordedAtMs ASC")
    suspend fun getUnboundWatchBatches(): List<PendingStepBatchEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(batch: PendingStepBatchEntity)

    @Query("UPDATE pending_step_batches SET synced = 1 WHERE batchId IN (:batchIds)")
    suspend fun markBatchesSynced(batchIds: List<String>)

    @Query("UPDATE pending_step_batches SET userId = :userId WHERE userId = '__WATCH_UNBOUND__'")
    suspend fun claimUnboundWatchBatches(userId: String)

    @Query("SELECT * FROM sync_logs WHERE userId = :userId ORDER BY timestampMs DESC LIMIT 20")
    fun observeRecentLogs(userId: String): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity)
}

@Database(
    entities = [PendingStepBatchEntity::class, SyncLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SolstrideLocalDatabase : RoomDatabase() {
    abstract fun offlineSyncDao(): OfflineSyncDao

    companion object {
        @Volatile
        private var INSTANCE: SolstrideLocalDatabase? = null

        fun getInstance(context: Context): SolstrideLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SolstrideLocalDatabase::class.java,
                    "solstride_offline_sync.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
