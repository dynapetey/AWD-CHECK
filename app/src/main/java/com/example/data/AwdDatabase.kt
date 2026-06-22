package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vin_scans")
data class VinScan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String,
    val timestamp: Long,
    val year: String,
    val make: String,
    val model: String,
    val driveType: String,
    val bodyClass: String = "",
    val vehicleType: String = "",
    val isClean: Boolean = true,
    val errorMsg: String? = null,
    val parkingBrake: String = "Unknown"
)

@Dao
interface VinScanDao {
    @Query("SELECT * FROM vin_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<VinScan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: VinScan): Long

    @Delete
    suspend fun deleteScan(scan: VinScan)

    @Query("DELETE FROM vin_scans")
    suspend fun clearHistory()
}

@Database(entities = [VinScan::class], version = 1, exportSchema = false)
abstract class AwdDatabase : RoomDatabase() {
    abstract fun vinScanDao(): VinScanDao

    companion object {
        @Volatile
        private var INSTANCE: AwdDatabase? = null

        fun getDatabase(context: Context): AwdDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AwdDatabase::class.java,
                    "awd_check_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
