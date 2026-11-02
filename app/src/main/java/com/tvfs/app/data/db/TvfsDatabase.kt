package com.tvfs.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelEntity::class,
        EpgProgramEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TvfsDatabase : RoomDatabase() {

    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao

    companion object {
        @Volatile
        private var INSTANCE: TvfsDatabase? = null

        fun getInstance(context: Context): TvfsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TvfsDatabase::class.java,
                    "tvfs_curated.db"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
