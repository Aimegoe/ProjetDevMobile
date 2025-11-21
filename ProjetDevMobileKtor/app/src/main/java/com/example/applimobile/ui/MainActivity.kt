package com.example.applimobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.room.Room
import coil.compose.AsyncImage
import com.example.applimobile.BuildConfig
import com.example.applimobile.data.local.AppDatabase
import com.example.applimobile.model.Film
import com.example.applimobile.model.FilmCredits
import com.example.applimobile.model.Message
import com.example.applimobile.repository.FilmRepository
import com.example.applimobile.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/* ------------------------------------
   Navigation : définition des écrans
------------------------------------ */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Liked : Screen("liked")
    object Chat : Screen("chat/{filmId}") {
        fun createRoute(filmId: Int) = "chat/$filmId"
    }
    object Detail : Screen("detail/{filmId}?fromLiked={fromLiked}") {
        fun createRoute(filmId: Int, fromLiked: Boolean) = "detail/$filmId?fromLiked=$fromLiked"
    }
}


class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: FilmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "database-name"
        ).fallbackToDestructiveMigration().build()

        repository = FilmRepository(database.filmDao(), database.messageDao())

        val viewModel: MainViewModel by viewModels {
            MainViewModel.Factory(BuildConfig.TMDB_API_KEY, repository)
        }

        viewModel.loadFilms()

        setContent {
            MyApp(viewModel)
        }
    }
}


@Composable
fun MyApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme {
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            /* ---------------- Home ---------------- */
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToLiked = { navController.navigate(Screen.Liked.route) },
                    onFilmClick = { film -> navController.navigate(Screen.Detail.createRoute(film.id, false)) },
                    snackbarHostState = snackbarHostState
                )
            }

            /* ---------------- Liked ---------------- */
            composable(Screen.Liked.route) {
                LikedScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onFilmClick = { film -> navController.navigate(Screen.Chat.createRoute(film.id)) },
                    onFilmDetailClick = { film -> navController.navigate(Screen.Detail.createRoute(film.id, true)) }
                )
            }

            /* ---------------- Chat ---------------- */
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("filmId") { type = NavType.IntType })
            ) { backStackEntry ->
                val filmId = backStackEntry.arguments?.getInt("filmId") ?: return@composable
                val films by viewModel.films.collectAsState(initial = emptyList())
                val film = films.find { it.id == filmId }

                if (film != null) {
                    FilmChatScreen(
                        viewModel = viewModel,
                        film = film,
                        onBack = { navController.popBackStack() },
                        onFilmDetailClick = { navController.navigate(Screen.Detail.createRoute(film.id, false)) }
                    )
                }
            }

            /* ---------------- Detail ---------------- */
            composable(
                route = Screen.Detail.route,
                arguments = listOf(
                    navArgument("filmId") { type = NavType.IntType },
                    navArgument("fromLiked") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val filmId = backStackEntry.arguments?.getInt("filmId") ?: return@composable
                val fromLiked = backStackEntry.arguments?.getBoolean("fromLiked") ?: false

                val films by viewModel.films.collectAsState(initial = emptyList())
                val film = films.find { it.id == filmId }
                val creditsMap by viewModel.filmCredits.collectAsState(initial = emptyMap())

                if (film != null) {
                    FilmDetailScreen(
                        viewModel = viewModel,
                        film = film,
                        fromLiked = fromLiked,
                        credits = creditsMap[film.id],
                        onBack = { navController.popBackStack() },
                        snackbarHostState = snackbarHostState,
                        coroutineScope = coroutineScope,
                        onReturnToLiked = { navController.popBackStack(Screen.Liked.route, false) },
                        onReturnHome = { navController.popBackStack(Screen.Home.route, false) }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onFilmClick: (Film) -> Unit,
    onFilmDetailClick: (Film) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.reloadSavedFilms()
    }

    val films by viewModel.films.collectAsState()
    val chats by viewModel.chats.collectAsState()
    var sortByDate by remember { mutableStateOf(false) }

    val likedFilms = films.filter { it.liked }
        .distinctBy { it.id }
        .let { list ->
            if (sortByDate) list.sortedBy { it.rendezVousDate ?: Long.MAX_VALUE } else list
        }

    val filmsWithLastMessage = likedFilms.map { film ->
        val lastMessage = chats[film.id]?.messages
            ?.filterNot { it.isDateMessage }
            ?.lastOrNull()
        film to lastMessage
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Films Likés") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        sortByDate = !sortByDate
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    }) {
                        Text(
                            text = if (sortByDate) "Trier : date" else "Trier par date",
                            color = Color(0xFF00C853)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        if (likedFilms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Vous n'avez liké aucun film.")
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(filmsWithLastMessage, key = { it.first.id }) { (film, lastMessage) ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                        .clickable { onFilmClick(film) },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    AsyncImage(
                        model = film.imageUrl,
                        contentDescription = film.title,
                        modifier = Modifier
                            .height(80.dp)
                            .widthIn(max = 60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = film.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        lastMessage?.let {
                            Text(
                                text = "${if (it.isUserMessage) "Vous : " else ""}${it.text}",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        film.rendezVousDate?.let { dateMillis ->
                            val formatted = remember(dateMillis) {
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
                                    .format(Date(dateMillis))
                            }
                            Text(
                                text = "📅 $formatted",
                                fontSize = 12.sp,
                                color = Color(0xFF00A3FF)
                            )
                        }
                    }

                    IconButton(onClick = { onFilmDetailClick(film) }) {
                        Icon(Icons.Filled.Info, contentDescription = "Détail")
                    }
                }
            }

        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmChatScreen(
    viewModel: MainViewModel,
    film: Film,
    onBack: () -> Unit,
    onFilmDetailClick: (Film) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }

    LaunchedEffect(film.id) {
        messages = viewModel.getMessagesSuspend(film.id)
    }

    if (showDatePicker) {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _: DatePicker, y, m, d ->
            val c = Calendar.getInstance()
            c.set(y, m, d, 0, 0, 0)
            pendingDateMillis = c.timeInMillis
            showDatePicker = false
            showTimePicker = true
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            .apply { setOnDismissListener { showDatePicker = false }; show() }
    }

    if (showTimePicker && pendingDateMillis != null) {
        val cal = Calendar.getInstance()
        TimePickerDialog(context, { _, h, min ->
            val c = Calendar.getInstance()
            c.timeInMillis = pendingDateMillis ?: System.currentTimeMillis()
            c.set(Calendar.HOUR_OF_DAY, h)
            c.set(Calendar.MINUTE, min)
            c.set(Calendar.SECOND, 0)
            val finalMillis = c.timeInMillis

            viewModel.setRendezVousDate(film.id, finalMillis)

            messages = messages.filterNot { it.isDateMessage }

            viewModel.deleteAllDateMessages(film.id)

            val dateMessage = Message(
                text = "📅 Rendez-vous : ${SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(finalMillis)}",
                dateTime = finalMillis,
                isUserMessage = true,
                isDateMessage = true
            )

            viewModel.sendMessage(film.id, dateMessage)
            messages = messages + dateMessage
            pendingDateMillis = null
            showTimePicker = false
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
            .apply { show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onFilmDetailClick(film) }.padding(end = 8.dp)
                    ) {
                        AsyncImage(
                            model = film.imageUrl,
                            contentDescription = film.title,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            film.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.Black)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    val isUser = msg.isUserMessage
                    val bubbleColor = if (msg.isDateMessage) MaterialTheme.colorScheme.tertiaryContainer
                    else if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                    val contentColor = if (msg.isDateMessage) MaterialTheme.colorScheme.onTertiaryContainer
                    else if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = bubbleColor,
                            tonalElevation = 2.dp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = msg.text, color = contentColor, fontSize = 16.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(msg.dateTime),
                                    color = contentColor.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                                if (msg.isDateMessage) {
                                    TextButton(onClick = {
                                        viewModel.deleteDateMessage(film.id, msg)
                                        viewModel.setRendezVousDate(film.id, null)
                                        messages = messages.filter { it != msg }
                                    }) {
                                        Text("Annuler", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Écrire un message...", color = Color.Gray) },
                    modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color.Black,
                        focusedContainerColor = Color(0xFFF0F0F0),
                        unfocusedContainerColor = Color(0xFFE0E0E0)
                    ),
                    maxLines = 1,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotBlank()) {
                            val userMessage = Message(
                                text = text,
                                dateTime = System.currentTimeMillis(),
                                isUserMessage = true
                            )
                            viewModel.sendMessage(film.id, userMessage)
                            messages = messages + userMessage
                            text = ""
                        }
                    })
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = { showDatePicker = true },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("📅", fontSize = 18.sp)
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            val userMessage = Message(
                                text = text,
                                dateTime = System.currentTimeMillis(),
                                isUserMessage = true
                            )
                            viewModel.sendMessage(film.id, userMessage)
                            messages = messages + userMessage
                            text = ""
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
                ) {
                    Text("✉️", fontSize = 18.sp)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToLiked: () -> Unit,
    onFilmClick: (Film) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val films by viewModel.filmsToSee.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val coroutine = rememberCoroutineScope()
    val currentFilm = films.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Films à découvrir") },
                actions = {
                    IconButton(onClick = onNavigateToLiked) { Icon(Icons.Filled.List, null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (films.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Vous avez liké ou disliké tous les films !")
                }
                return@Scaffold
            }

            currentFilm?.let { film ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .padding(16.dp)
                        .clickable { onFilmClick(film) },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = film.imageUrl,
                            contentDescription = film.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = film.title ?: "Titre manquant",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.dislikeCurrentFilm() },
                        containerColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dislike",
                            tint = Color.Red,
                            modifier = Modifier.size(50.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            viewModel.likeCurrentFilm()
                            coroutine.launch {
                                snackbarHostState.showSnackbar("🎬 Match avec ${film.title} !")
                                delay(1500)
                                snackbarHostState.currentSnackbarData?.dismiss()
                            }
                        },
                        containerColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Like",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
            }
        }
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmDetailScreen(
    viewModel: MainViewModel,
    film: Film,
    fromLiked: Boolean,
    credits: FilmCredits?,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    onReturnToLiked: () -> Unit,
    onReturnHome: () -> Unit
) {
    val isLiked = film.liked

    LaunchedEffect(film.id) {
        viewModel.loadFilmCredits(film.id)
    }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = film.imageUrl,
            contentDescription = film.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        Column(
            Modifier.align(Alignment.BottomStart).padding(24.dp)
        ) {
            Text(film.title, fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(film.overview ?: "Aucun synopsis disponible.", color = Color.White.copy(alpha = 0.9f), maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))

            credits?.let { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    c.directorImage?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = c.director,
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Réalisateur : ${c.director}", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))

                LazyRow {
                    items(c.actors) { actor ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 12.dp)) {
                            AsyncImage(
                                model = actor.imageUrl,
                                contentDescription = actor.name,
                                modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(actor.name, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FloatingActionButton(
                    onClick = {
                        if (isLiked) { viewModel.unlikeFromDetail(film.id); onReturnToLiked() }
                        else { viewModel.dislikeCurrentFilm(); onReturnHome() }
                    },
                    containerColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(70.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Annuler", tint = Color.Red, modifier = Modifier.size(40.dp))
                }

                if (!isLiked) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.likeCurrentFilm()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("🎬 Match avec ${film.title} !")
                                delay(1200)
                                snackbarHostState.currentSnackbarData?.dismiss()
                            }
                            onReturnHome()
                        },
                        containerColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Like", tint = Color(0xFF00E676), modifier = Modifier.size(40.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onBack,
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.padding(20.dp).align(Alignment.TopStart).size(56.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
        }
    }
}
