package com.example.applimobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.applimobile.model.*
import com.example.applimobile.repository.FilmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val apiKey: String,
    private val repository: FilmRepository
) : ViewModel() {


    private val _films = MutableStateFlow<List<Film>>(emptyList())
    val films = _films.asStateFlow()


    val filmsToSee: StateFlow<List<Film>> =
        _films.map { list ->
            list.filter { !it.liked && !it.unliked }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val likedFilms: StateFlow<List<Film>> =
        _films.map { it.filter { f -> f.liked } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unlikedFilms: StateFlow<List<Film>> =
        _films.map { it.filter { f -> f.unliked } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    private val _currentIndex = MutableStateFlow(0)
    val currentIndex = _currentIndex.asStateFlow()


    private val _filmCredits = MutableStateFlow<Map<Int, FilmCredits>>(emptyMap())
    val filmCredits = _filmCredits.asStateFlow()

    private val _chats = MutableStateFlow<Map<Int, FilmChat>>(emptyMap())
    val chats: StateFlow<Map<Int, FilmChat>> = _chats


    init {
        viewModelScope.launch(Dispatchers.IO) {
            val liked = repository.getLikedFilms()
            val likedIds = liked.map { it.id }

            _films.value = _films.value.map { film ->
                if (likedIds.contains(film.id)) film.copy(liked = true) else film
            }
        }
    }


    fun loadFilms(pages: Int = 5) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = mutableListOf<Film>()
                for (page in 1..pages) {
                    all += repository.getPopularFilms(apiKey, page)
                }

                val liked = repository.getLikedFilms().map { it.id }

                _films.value = all.map { f ->
                    if (liked.contains(f.id)) f.copy(liked = true) else f
                }.shuffled()

                _currentIndex.value = 0

                loadChatsForLikedFilms()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun likeCurrentFilm() {
        val list = filmsToSee.value
        val film = list.getOrNull(_currentIndex.value) ?: return

        updateFilm(film.copy(liked = true, unliked = false))
        _currentIndex.value = 0
    }

    fun dislikeCurrentFilm() {
        val list = filmsToSee.value
        val film = list.getOrNull(_currentIndex.value) ?: return

        updateFilm(film.copy(liked = false, unliked = true))
        _currentIndex.value = 0
    }

    private fun updateFilm(updated: Film) {
        _films.value = _films.value.map {
            if (it.id == updated.id) updated else it
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (updated.liked) repository.likeFilm(updated)
            else repository.unlikeFilm(updated.id)
        }
    }


    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index
    }

    fun loadFilmCredits(filmId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = repository.getFilmCredits(apiKey, filmId)

                val actors = response.cast.take(10).map { actor ->
                    Actor(
                        name = actor.name,
                        imageUrl = actor.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
                    )
                }

                val directorCrew = response.crew.firstOrNull { it.job == "Director" }
                val directorName = directorCrew?.name ?: "N/A"
                val directorImage = directorCrew?.profilePath?.let {
                    "https://image.tmdb.org/t/p/w185$it"
                }

                _filmCredits.value =
                    _filmCredits.value + (filmId to FilmCredits(directorName, directorImage, actors))

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun likeFromDetail(filmId: Int) {
        val film = _films.value.firstOrNull { it.id == filmId } ?: return
        updateFilm(film.copy(liked = true, unliked = false))
    }

    fun unlikeFromDetail(filmId: Int) {
        val film = _films.value.firstOrNull { it.id == filmId } ?: return
        updateFilm(film.copy(liked = false, unliked = false))
    }


    suspend fun getMessagesSuspend(filmId: Int): List<Message> {
        val messages = repository.getMessages(filmId)
        val existing = _chats.value[filmId] ?: FilmChat(filmId)
        existing.messages.clear()
        existing.messages.addAll(messages)
        _chats.value = _chats.value + (filmId to existing)
        return messages
    }

    fun sendMessage(filmId: Int, message: Message) =
        viewModelScope.launch {
            repository.sendMessage(filmId, message)
            getMessagesSuspend(filmId)
        }

    fun deleteDateMessage(filmId: Int, message: Message) =
        viewModelScope.launch {
            repository.deleteDateMessage(filmId, message)
            getMessagesSuspend(filmId)
        }

    fun deleteAllDateMessages(filmId: Int) = viewModelScope.launch {
        repository.deleteAllDateMessages(filmId)
        getMessagesSuspend(filmId)
    }


    fun loadChatsForLikedFilms() {
        viewModelScope.launch(Dispatchers.IO) {
            val liked = _films.value.filter { it.liked }
            val map = mutableMapOf<Int, FilmChat>()
            liked.forEach { film ->
                val messages = repository.getMessages(film.id)
                map[film.id] = FilmChat(film.id, messages.toMutableList())
            }
            _chats.value = map
        }
    }

    fun setRendezVousDate(filmId: Int, dateMillis: Long?) {
        val updatedList = _films.value.toMutableList()
        val index = updatedList.indexOfFirst { it.id == filmId }
        if (index == -1) return

        val updatedFilm = updatedList[index].copy(rendezVousDate = dateMillis)
        updatedList[index] = updatedFilm
        _films.value = updatedList

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveFilm(updatedFilm)
        }
    }

    fun reloadSavedFilms() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedFilms = repository.getLikedFilms()
            val savedMap = savedFilms.associateBy { it.id }

            _films.value = _films.value.map { film ->
                val saved = savedMap[film.id]
                if (saved != null) {
                    film.copy(
                        liked = true,
                        rendezVousDate = saved.rendezVousDate
                    )
                } else film
            }
        }
    }





    class Factory(
        private val apiKey: String,
        private val repository: FilmRepository
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(apiKey, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
