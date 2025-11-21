package com.example.applimobile.repository

import com.example.applimobile.data.local.FilmDao
import com.example.applimobile.data.local.MessageDao
import com.example.applimobile.data.local.MessageEntity
import com.example.applimobile.data.model.toEntity
import com.example.applimobile.data.model.toFilm
import com.example.applimobile.model.CreditResponse
import com.example.applimobile.model.Film
import com.example.applimobile.model.FilmResponse
import com.example.applimobile.model.Message
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


class FilmRepository(
    private val filmDao: FilmDao,
    private val messageDao: MessageDao
) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl = "https://api.themoviedb.org/3"


    suspend fun getPopularFilms(apiKey: String, page: Int = 1): List<Film> {
        val response: FilmResponse =
            client.get("$baseUrl/movie/popular?api_key=$apiKey&page=$page&language=fr-FR").body()
        return response.results
    }


    suspend fun getFilmCredits(apiKey: String, filmId: Int): CreditResponse {
        return client.get("$baseUrl/movie/$filmId/credits?api_key=$apiKey&language=fr-FR").body()
    }


    suspend fun getLikedFilms(): List<Film> {
        return filmDao.getFavFilms().map { it.toFilm() }
    }


    suspend fun likeFilm(film: Film) {
        filmDao.insertFilm(film.toEntity())
    }


    suspend fun unlikeFilm(filmId: Int) {
        filmDao.deleteFilm(filmId)
    }


    suspend fun saveFilm(film: Film) {
        filmDao.insertFilm(film.toEntity())
    }


    suspend fun getMessages(filmId: Int): List<Message> {
        return messageDao.getMessagesForFilm(filmId).map { entity ->
            Message(
                id = entity.id,
                text = entity.text,
                dateTime = entity.dateTime,
                isUserMessage = entity.isUserMessage,
                isDateMessage = entity.isDateMessage
            )
        }
    }


    suspend fun sendMessage(filmId: Int, message: Message) {
        val entity = MessageEntity(
            id = 0,
            filmId = filmId,
            text = message.text,
            dateTime = message.dateTime,
            isUserMessage = message.isUserMessage,
            isDateMessage = message.isDateMessage
        )
        messageDao.insertMessage(entity)
    }


    suspend fun deleteDateMessage(filmId: Int, message: Message) {
        val entity = MessageEntity(
            id = message.id,
            filmId = filmId,
            text = message.text,
            dateTime = message.dateTime,
            isUserMessage = message.isUserMessage,
            isDateMessage = message.isDateMessage
        )
        messageDao.deleteMessage(entity)
    }


    suspend fun deleteAllDateMessages(filmId: Int) {
        messageDao.deleteDateMessagesForFilm(filmId)
    }

    suspend fun deleteMessage(message: MessageEntity) {
        messageDao.deleteMessage(message)
    }
}
