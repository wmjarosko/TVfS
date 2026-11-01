package com.tvfs.app.data.db

import androidx.room.*

@Dao
interface EpgDao {

    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE channelEpgId = :epgId 
          AND startEpochMs <= :currentTimeMs 
          AND stopEpochMs > :currentTimeMs 
        ORDER BY startEpochMs ASC 
        LIMIT 1
        """
    )
    fun getCurrentProgram(epgId: String, currentTimeMs: Long): EpgProgramEntity?

    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE channelEpgId = :epgId 
          AND startEpochMs >= :currentTimeMs 
        ORDER BY startEpochMs ASC 
        LIMIT 1
        """
    )
    fun getNextProgram(epgId: String, currentTimeMs: Long): EpgProgramEntity?

    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE channelEpgId = :epgId 
          AND stopEpochMs > :startWindowMs 
          AND startEpochMs < :endWindowMs 
        ORDER BY startEpochMs ASC
        """
    )
    fun getScheduleForChannel(epgId: String, startWindowMs: Long, endWindowMs: Long): List<EpgProgramEntity>

    @Query("SELECT COUNT(*) FROM epg_programs")
    fun getTotalProgramCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE stopEpochMs < :cutoffEpochMs")
    fun purgeExpiredPrograms(cutoffEpochMs: Long): Int

    @Query("DELETE FROM epg_programs")
    fun clearAll()

    @Transaction
    fun replaceAllPrograms(programs: List<EpgProgramEntity>) {
        clearAll()
        insertPrograms(programs)
    }
}
