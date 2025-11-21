package com.example.applimobile.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface FilmDao {
    @Query("SELECT * FROM filmentity")
    suspend fun getFavFilms(): List<FilmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilm(film: FilmEntity)

    @Query("DELETE FROM filmentity WHERE id = :id")
    suspend fun deleteFilm(id: Int)

    @Query("UPDATE filmentity SET rendezVousDate = :date WHERE id = :filmId")
    suspend fun updateRendezVousDate(filmId: Int, date: Long?)

}


@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE filmId = :filmId ORDER BY dateTime ASC")
    suspend fun getMessagesForFilm(filmId: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE filmId = :filmId AND isDateMessage = 1")
    suspend fun deleteDateMessagesForFilm(filmId: Int)
}
