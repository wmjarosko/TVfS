package com.tvfs.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.tvfs.app.data.TvfsRepository
import com.tvfs.app.model.MovieItem
import com.tvfs.app.ui.theme.TvfsActionButton
import com.tvfs.app.ui.theme.TvfsFocusableSurface
import com.tvfs.app.ui.theme.TvfsTokens

/**
 * Icon 3: Movies Direct Entry Activity (SDS Section 2.1).
 * Launches directly into the curated film catalogue with large, high-contrast cards.
 */
class MovieBrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = TvfsRepository.getBootstrapConfig(this)
        val movies = TvfsRepository.getCuratedMovies(config)

        setContent {
            val firstCardFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                runCatching { firstCardFocus.requestFocus() }
            }

            MovieBrowserScreen(
                movies = movies,
                firstCardFocus = firstCardFocus,
                onPlayMovie = { movie ->
                    TvfsRepository.saveLastMovieViewed(this, movie)
                    val intent = Intent(this, LivePlayerActivity::class.java).apply {
                        putExtra(LivePlayerActivity.EXTRA_MODE, "MOVIE")
                        putExtra(LivePlayerActivity.EXTRA_TITLE, movie.title)
                        putExtra(
                            LivePlayerActivity.EXTRA_SUBTITLE,
                            "${movie.genre} (${movie.releaseYear}) • ${movie.durationMinutes} min"
                        )
                        putExtra(LivePlayerActivity.EXTRA_STREAM_URL, movie.streamUrl)
                    }
                    startActivity(intent)
                },
                onOpenSearch = {
                    startActivity(Intent(this, SearchActivity::class.java))
                },
                onBackToHub = {
                    finish()
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieBrowserScreen(
    movies: List<MovieItem>,
    firstCardFocus: FocusRequester,
    onPlayMovie: (MovieItem) -> Unit,
    onOpenSearch: () -> Unit,
    onBackToHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvfsTokens.ScreenBackground)
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Movies",
                    color = TvfsTokens.PureWhite,
                    fontSize = TvfsTokens.PrimaryHeadingSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select any film and press OK to start watching immediately",
                    color = TvfsTokens.SecondaryGray,
                    fontSize = TvfsTokens.SecondaryMetadataSize
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvfsActionButton(
                    label = "SEARCH",
                    onClick = onOpenSearch
                )
                TvfsActionButton(
                    label = "TV HUB",
                    onClick = onBackToHub
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(movies) { index, movie ->
                TvfsFocusableSurface(
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (index == 0) firstCardFocus else null,
                    onClick = { onPlayMovie(movie) }
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[MOVIE] • ${movie.genre} (${movie.releaseYear})",
                                color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.SecondaryGray,
                                fontSize = TvfsTokens.MinimumTextSize,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = movie.title,
                                color = TvfsTokens.PureWhite,
                                fontSize = TvfsTokens.PrimaryHeadingSize,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = movie.description,
                                color = TvfsTokens.SecondaryGray,
                                fontSize = TvfsTokens.SecondaryMetadataSize,
                                maxLines = 2
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        Text(
                            text = "[ WATCH NOW • ${movie.durationMinutes}m ]",
                            color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.PureWhite,
                            fontSize = TvfsTokens.ProgramTitleSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
