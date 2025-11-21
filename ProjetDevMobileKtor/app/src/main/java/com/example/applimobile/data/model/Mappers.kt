package com.example.applimobile.data.model

import com.example.applimobile.data.local.FilmEntity
import com.example.applimobile.model.Film


fun Film.toEntity(): FilmEntity = FilmEntity(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    liked = liked,
    unliked = unliked,
    rendezVousDate = rendezVousDate
)


fun FilmEntity.toFilm(): Film = Film(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    liked = liked,
    unliked = unliked,
    rendezVousDate = rendezVousDate
)