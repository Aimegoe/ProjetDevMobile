package com.example.applimobile.data.remote

import com.example.applimobile.BuildConfig
import com.example.applimobile.model.FilmResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*


class FilmApi(private val client: HttpClient) {

    suspend fun getPopularMovies(page: Int = 1, language: String = "fr-FR"): FilmResponse {
        return client.get("https://api.themoviedb.org/3/movie/popular") {
            parameter("api_key", BuildConfig.TMDB_API_KEY)
            parameter("language", language)
            parameter("page", page)
        }.body()
    }
}
