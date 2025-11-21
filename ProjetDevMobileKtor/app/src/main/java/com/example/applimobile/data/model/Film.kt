package com.example.applimobile.model

import androidx.room.ColumnInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Film(
    val id: Int,
    val title: String,
    val overview: String? = "",
    var liked: Boolean = false,
    val unliked: Boolean = false,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = "",
    @SerialName("vote_average") val voteAverage: Double? = 0.0,
    @ColumnInfo(name = "rendezVousDate")val rendezVousDate: Long? = null
) {
    val imageUrl: String
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: ""
}



@Serializable
data class FilmResponse(
    val page: Int,
    val results: List<Film>,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Int
)

data class Message(
    val id: Int = 0,
    val text: String,
    val dateTime: Long,
    val isUserMessage: Boolean,
    val isDateMessage: Boolean = false
)


data class FilmChat(
    val filmId: Int,
    val messages: MutableList<Message> = mutableListOf()
)
@Serializable
data class CreditResponse(
    val id: Int,
    val cast: List<Cast>,
    val crew: List<Crew>
)

@Serializable
data class Cast(
    val name: String,
    val character: String,
    @SerialName("profile_path") val profilePath: String? = null
)

@Serializable
data class Crew(
    val name: String,
    val job: String,
    @SerialName("profile_path") val profilePath: String? = null
)

data class Actor(
    val name: String,
    val imageUrl: String? = null
)

data class FilmCredits(
    val director: String,
    val directorImage: String?,
    val actors: List<Actor>
)




