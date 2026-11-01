package com.tvfs.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY channelNumber ASC")
    fun getAllChannels(): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY channelNumber ASC")
    fun observeAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE channelNumber = :channelNumber LIMIT 1")
    fun getChannelByNumber(channelNumber: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE epgId = :epgId LIMIT 1")
    fun getChannelByEpgId(epgId: String): ChannelEntity?

    @Query("SELECT COUNT(*) FROM channels")
    fun getChannelCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    fun clearAll()

    @Transaction
    fun replaceAllChannels(channels: List<ChannelEntity>) {
        clearAll()
        insertChannels(channels)
    }
}
