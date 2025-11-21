package com.example.applimobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class FilmEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String? = "",
    val posterPath: String? = null,
    val releaseDate: String? = "",
    val voteAverage: Double? = 0.0,
    val liked: Boolean = true,
    val unliked: Boolean = false,
    val rendezVousDate: Long? = null
)



@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filmId: Int,
    val text: String,
    val dateTime: Long,
    val isUserMessage: Boolean,
    val isDateMessage: Boolean
)


