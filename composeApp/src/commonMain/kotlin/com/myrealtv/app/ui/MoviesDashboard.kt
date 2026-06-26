package com.myrealtv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.myrealtv.app.data.Repository
import com.myrealtv.app.data.XtreamCategory
import com.myrealtv.app.data.XtreamMovie
import com.myrealtv.app.data.XtreamMovieDetails
import com.myrealtv.app.ui.components.BackHandler
import com.myrealtv.app.ui.components.ChannelLogo
import com.myrealtv.app.ui.components.TvButton
import com.myrealtv.app.ui.components.TvFocusableCard
import com.myrealtv.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MoviesDashboard(
    repository: Repository,
    onPlayMovie: (Int) -> Unit
) {
    val continueWatching = remember(repository.watchHistory.collectAsState().value) {
        repository.getContinueWatchingMovies()
    }
    
    val latestMovies = remember(repository.vodMovies.collectAsState().value) {
        repository.getLatestAddedMovies()
    }

    val categories = repository.vodCategories.collectAsState().value
    val allMovies = repository.vodMovies.collectAsState().value

    val historyMap = repository.watchHistory.collectAsState().value
        .filter { it.type == "movie" }
        .associateBy { it.itemId }

    val coroutineScope = rememberCoroutineScope()
    var selectedMovieForDetails by remember { mutableStateOf<XtreamMovie?>(null) }
    var movieDetails by remember { mutableStateOf<XtreamMovieDetails?>(null) }
    var isMovieDetailsLoading by remember { mutableStateOf(false) }
    var activeGridViewCategory by remember { mutableStateOf<XtreamCategory?>(null) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedMovieForDetails != null || activeGridViewCategory != null || showFavoritesOnly) {
        if (selectedMovieForDetails != null) {
            selectedMovieForDetails = null
            movieDetails = null
        } else if (showFavoritesOnly) {
            showFavoritesOnly = false
        } else {
            activeGridViewCategory = null
        }
    }

    if (selectedMovieForDetails != null) {
        val movie = selectedMovieForDetails!!
        MovieDetailsPage(
            movie = movie,
            movieDetails = movieDetails,
            isDetailsLoading = isMovieDetailsLoading,
            favoritesList = repository.favorites.collectAsState().value,
            onPlayMovie = {
                selectedMovieForDetails = null
                movieDetails = null
                onPlayMovie(movie.streamId)
            },
            onToggleFavorite = {
                coroutineScope.launch {
                    repository.toggleFavorite("movie", movie.streamId.toString())
                }
            },
            onBack = {
                selectedMovieForDetails = null
                movieDetails = null
            }
        )
    } else if (showFavoritesOnly) {
        val favoritesList = repository.favorites.collectAsState().value
        val favoriteMovies = remember(allMovies, favoritesList) {
            val favIds = favoritesList.filter { it.type == "movie" }.map { it.itemId }.toSet()
            allMovies.filter { favIds.contains(it.streamId.toString()) }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorite Movies",
                    style = TvTypography.Title
                )
                TvButton(
                    text = "< Back to Dashboard",
                    onClick = { showFavoritesOnly = false }
                )
            }

            if (favoriteMovies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No favorite movies added yet", style = TvTypography.Subtitle, color = TextColorSecondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    gridItems(favoriteMovies) { movie ->
                        MovieCard(
                            movie = movie,
                            progress = historyMap[movie.streamId.toString()]?.let {
                                if (it.durationMs > 0) it.progressMs.toFloat() / it.durationMs.toFloat() else 0f
                            },
                            onClick = {
                                selectedMovieForDetails = movie
                                isMovieDetailsLoading = true
                                coroutineScope.launch {
                                    movieDetails = repository.xtreamClient.getMovieDetails(repository.activeProvider!!, movie.streamId)
                                    isMovieDetailsLoading = false
                                }
                            }
                        )
                    }
                }
            }
        }
    } else if (activeGridViewCategory != null) {
        val category = activeGridViewCategory!!
        val gridMovies = remember(allMovies, category) {
            allMovies.filter { it.categoryId == category.id }
                .sortedBy { it.name.lowercase() }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Movies - ${category.name}",
                    style = TvTypography.Title
                )
                TvButton(
                    text = "< Back to Categories",
                    onClick = { activeGridViewCategory = null }
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                gridItems(gridMovies) { movie ->
                    MovieCard(
                        movie = movie,
                        progress = historyMap[movie.streamId.toString()]?.let {
                            if (it.durationMs > 0) it.progressMs.toFloat() / it.durationMs.toFloat() else 0f
                        },
                        onClick = {
                            selectedMovieForDetails = movie
                            isMovieDetailsLoading = true
                            coroutineScope.launch {
                                movieDetails = repository.xtreamClient.getMovieDetails(repository.activeProvider!!, movie.streamId)
                                isMovieDetailsLoading = false
                            }
                        }
                    )
                }
            }
        }
    } else {
        val categoriesWithMovies = remember(categories, allMovies) {
            categories.filter { cat ->
                allMovies.any { it.categoryId == cat.id }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Movies Dashboard",
                        style = TvTypography.Title
                    )
                    TvFocusableCard(
                        onClick = { showFavoritesOnly = true },
                        modifier = Modifier.height(36.dp)
                    ) { isFocused ->
                        Text(
                            text = "★ Favorites",
                            color = if (isFocused) TextColorPrimary else AccentColorLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            // Row 1: Continue Watching
            if (continueWatching.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Continue Watching",
                            style = TvTypography.Subtitle,
                            color = AccentColorLight
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            items(continueWatching) { movie ->
                                val history = historyMap[movie.streamId.toString()]
                                val progress = if (history != null && history.durationMs > 0) {
                                    history.progressMs.toFloat() / history.durationMs.toFloat()
                                } else 0f

                                MovieCard(
                                    movie = movie,
                                    progress = progress,
                                    onClick = {
                                        selectedMovieForDetails = movie
                                        isMovieDetailsLoading = true
                                        coroutineScope.launch {
                                            movieDetails = repository.xtreamClient.getMovieDetails(repository.activeProvider!!, movie.streamId)
                                            isMovieDetailsLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Row 2: Latest Added Media
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Latest Added Media",
                        style = TvTypography.Subtitle,
                        color = AccentColorLight
                    )
                    if (latestMovies.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No movies available", style = TvTypography.Detail)
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            items(latestMovies) { movie ->
                                MovieCard(
                                    movie = movie,
                                    progress = null,
                                    onClick = {
                                        selectedMovieForDetails = movie
                                        isMovieDetailsLoading = true
                                        coroutineScope.launch {
                                            movieDetails = repository.xtreamClient.getMovieDetails(repository.activeProvider!!, movie.streamId)
                                            isMovieDetailsLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Row 3+: Categories Rows
            items(categoriesWithMovies) { category ->
                val categoryMovies = remember(allMovies, category) {
                    allMovies.filter { it.categoryId == category.id }
                        .sortedWith(compareByDescending { it.added ?: "" })
                        .take(15)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.name,
                            style = TvTypography.Subtitle,
                            color = AccentColorLight
                        )
                        TvFocusableCard(
                            onClick = { activeGridViewCategory = category },
                            modifier = Modifier.height(36.dp)
                        ) { isFocused ->
                            Text(
                                text = "View All >",
                                color = if (isFocused) TextColorPrimary else AccentColorLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        items(categoryMovies) { movie ->
                            MovieCard(
                                movie = movie,
                                progress = historyMap[movie.streamId.toString()]?.let {
                                    if (it.durationMs > 0) it.progressMs.toFloat() / it.durationMs.toFloat() else 0f
                                },
                                onClick = {
                                    selectedMovieForDetails = movie
                                    isMovieDetailsLoading = true
                                    coroutineScope.launch {
                                        movieDetails = repository.xtreamClient.getMovieDetails(repository.activeProvider!!, movie.streamId)
                                        isMovieDetailsLoading = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieDetailsPage(
    movie: XtreamMovie,
    movieDetails: XtreamMovieDetails?,
    isDetailsLoading: Boolean,
    favoritesList: List<com.myrealtv.app.data.FavoriteRecord>,
    onPlayMovie: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val playButtonFocusRequester = remember { FocusRequester() }
    val isFav = remember(favoritesList, movie.streamId) {
        favoritesList.any { it.type == "movie" && it.itemId == movie.streamId.toString() }
    }

    LaunchedEffect(movie.streamId) {
        try {
            playButtonFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left Side: Poster
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceColorHover)
            ) {
                ChannelLogo(
                    url = movie.icon,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Right Side: Info Panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Movie Name
                Text(
                    text = movie.name,
                    style = TvTypography.Title.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (isDetailsLoading) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentColorLight)
                    }
                } else {
                    val info = movieDetails?.info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Subtitle row (Release Year, Duration, Rating, Genre)
                        val ratingStr = info?.rating?.ifBlank { null } ?: movie.rating?.ifBlank { null }
                        val releaseDate = info?.releaseDate?.ifBlank { null } ?: movie.added?.split(" ")?.firstOrNull()
                        val durationStr = info?.duration?.let {
                            val totalSecs = it.toIntOrNull()
                            if (totalSecs != null && totalSecs > 0) {
                                "${totalSecs / 60} min"
                            } else {
                                it
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ratingStr != null && ratingStr != "0") {
                                Text(
                                    text = "★ $ratingStr",
                                    color = Color.Yellow,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (releaseDate != null) {
                                Text(
                                    text = "Release: $releaseDate",
                                    style = TvTypography.Detail,
                                    fontSize = 14.sp
                                )
                            }
                            if (durationStr != null) {
                                Text(
                                    text = "Duration: $durationStr",
                                    style = TvTypography.Detail,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Genre
                        if (!info?.genre.isNullOrBlank()) {
                            Text(
                                text = "Genre: ${info?.genre}",
                                style = TvTypography.Body.copy(color = TextColorSecondary)
                            )
                        }

                        // Director
                        if (!info?.director.isNullOrBlank()) {
                            Text(
                                text = "Director: ${info?.director}",
                                style = TvTypography.Body.copy(color = TextColorSecondary)
                            )
                        }

                        // Cast
                        if (!info?.cast.isNullOrBlank()) {
                            Text(
                                text = "Cast: ${info?.cast}",
                                style = TvTypography.Body.copy(color = TextColorSecondary),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Plot
                        val plot = info?.plot ?: "No description available for this movie."
                        Text(
                            text = plot,
                            style = TvTypography.Body,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Action Buttons Row (Play, Favorite, Back)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    TvFocusableCard(
                        onClick = onPlayMovie,
                        modifier = Modifier.focusRequester(playButtonFocusRequester)
                    ) { isFocused ->
                        Text(
                            text = "Play Movie",
                            color = if (isFocused) TextColorPrimary else AccentColorLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    TvFocusableCard(
                        onClick = onToggleFavorite
                    ) { isFocused ->
                        Text(
                            text = if (isFav) "Remove Favorite" else "Add Favorite",
                            color = if (isFocused) TextColorPrimary else AccentColorLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    TvFocusableCard(
                        onClick = onBack
                    ) { isFocused ->
                        Text(
                            text = "Back",
                            color = if (isFocused) TextColorPrimary else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: XtreamMovie,
    progress: Float?,
    onClick: () -> Unit
) {
    TvFocusableCard(
        onClick = onClick,
        modifier = Modifier.size(width = 160.dp, height = 220.dp)
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceColorHover),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(
                    url = movie.icon,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    movie.rating?.let {
                        if (it.isNotBlank() && it != "0") {
                            Text(
                                text = "★ $it",
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = movie.name,
                    style = TvTypography.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        color = AccentColorLight,
                        trackColor = SurfaceColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
