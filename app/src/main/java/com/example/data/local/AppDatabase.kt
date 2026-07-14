package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.example.data.model.DownloadItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadItemDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllTracks(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: DownloadItem)

    @Query("DELETE FROM downloaded_tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)

    @Query("SELECT * FROM downloaded_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): DownloadItem?
}

@Database(entities = [DownloadItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadItemDao(): DownloadItemDao
}
