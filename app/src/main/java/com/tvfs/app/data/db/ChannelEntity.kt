package com.tvfs.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a curated Live TV channel ingested from XC-PLaPA (seniortv_config.json).
 */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["epgId"]),
        Index(value = ["groupName"])
    ]
)
data class ChannelEntity(
    @PrimaryKey
    val channelNumber: Int,
    val name: String,
    val streamUrl: String,
    val logo: String? = null,
    val epgId: String,
    val groupName: String,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0
)
