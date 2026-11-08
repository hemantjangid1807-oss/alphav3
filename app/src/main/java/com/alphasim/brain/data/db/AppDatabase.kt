package com.alphasim.brain.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlphaListEntity::class, AlphaEntity::class, CacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun alphaDao(): AlphaDao
    abstract fun cacheDao(): CacheDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "alphasim.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
