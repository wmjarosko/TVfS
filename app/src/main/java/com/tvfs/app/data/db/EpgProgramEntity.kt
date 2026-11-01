package com.tvfs.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an EPG program entry ingested from XC-PLaPA (pruned_epg.xml).
 * Stores UTC epoch timestamps with indexes for fast "Now & Next" lookups and expired schedule purging.
 */
@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["channelEpgId"]),
        Index(value = ["channelEpgId", "startEpochMs"]),
        Index(value = ["channelEpgId", "stopEpochMs"]),
        Index(value = ["stopEpochMs"])
    ]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startEpochMs: Long,
    val stopEpochMs: Long
)
