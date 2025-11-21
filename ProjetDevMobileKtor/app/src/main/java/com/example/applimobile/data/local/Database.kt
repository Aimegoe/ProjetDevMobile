package com.example.applimobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase


@Database(entities = [FilmEntity::class, MessageEntity::class], version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun filmDao(): FilmDao
}