package com.example.visualfeedtask.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visualfeedtask.R
import com.example.visualfeedtask.data.model.Story
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import java.io.File
import com.example.visualfeedtask.ui.theme.VisualFeedTaskTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FeedRoute(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    //This is a single screen project, navigation is not required.
    FeedScreen(uiState = uiState, modifier = modifier, onIntent = viewModel::onIntent)
}

@Composable
fun FeedScreen(
    uiState: FeedUiState,
    modifier: Modifier = Modifier,
    onIntent: (FeedIntent) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val currentOnIntent by rememberUpdatedState(onIntent)
    val ready = uiState as? FeedUiState.Ready
    LaunchedEffect(ready?.stories?.size, ready?.hasMore, ready?.loadMoreFailed) {
        if (ready == null || !ready.hasMore || ready.loadMoreFailed) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.index == ready.stories.lastIndex }
        }.distinctUntilChanged().collect { reachedBottom ->
            if (reachedBottom) currentOnIntent(FeedIntent.ReachedBottom)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feed_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        when (uiState) {
            FeedUiState.Loading -> CircularProgressIndicator()
            FeedUiState.Error -> {
                Text(text = stringResource(R.string.feed_load_error))
                Button(onClick = { onIntent(FeedIntent.Retry) }) {
                    Text(text = stringResource(R.string.feed_retry))
                }
            }
            is FeedUiState.Ready -> {
                if (uiState.isOffline) {
                    Text(text = stringResource(R.string.feed_offline))
                }
                if (uiState.stories.isEmpty()) {
                    Text(text = stringResource(R.string.feed_empty))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.stories, key = { it.id }) { story ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                if (uiState.imageGenerationEnabled) {
                                    StoryThumbnail(
                                        story = story,
                                        failed = story.id in uiState.failedThumbnails,
                                        generating = story.id in uiState.loadingThumbnails,
                                        queued = story.id in uiState.queuedThumbnails,
                                        onIntent = onIntent,
                                    )
                                } else {
                                    DefaultThumbnail()
                                }
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(story.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(
                                            R.string.feed_story_metadata, story.author, story.score,
                                        ),
                                    )
                                }
                            }
                        }
                        if (uiState.isLoadingMore) {
                            item(key = "loading") { CircularProgressIndicator() }
                        }
                        if (uiState.loadMoreFailed) {
                            item(key = "retry") {
                                Column {
                                    Text(stringResource(R.string.feed_load_more_error))
                                    Button(onClick = { onIntent(FeedIntent.Retry) }) {
                                        Text(stringResource(R.string.feed_retry))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultThumbnail() {
    val description = stringResource(R.string.feed_default_thumbnail_description)
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Adaptive launcher icons are unsupported by painterResource, so draw its vector layers.
        Box(modifier = Modifier.size(96.dp).clip(CircleShape)) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StoryThumbnail(
    story: Story,
    failed: Boolean,
    generating: Boolean,
    queued: Boolean,
    onIntent: (FeedIntent) -> Unit,
) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(story.id) {
        currentOnIntent(FeedIntent.RequestThumbnail(story.id))
    }
    var imageLoaded by remember(story.thumbnailPath, generating) { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (story.thumbnailPath != null && (!failed || generating)) {
            key(story.thumbnailPath, generating) {
                AsyncImage(
                    model = File(story.thumbnailPath),
                    contentDescription = stringResource(R.string.feed_thumbnail_description, story.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        imageLoaded = state is AsyncImagePainter.State.Success
                        if (state is AsyncImagePainter.State.Error) {
                            currentOnIntent(FeedIntent.ThumbnailLoadFailed(story.id))
                        }
                    },
                )
            }
        }
        if (queued && !imageLoaded) {
            Text(stringResource(R.string.feed_thumbnail_queued))
        } else if (failed && !generating) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.feed_thumbnail_error))
                Button(onClick = { onIntent(FeedIntent.RetryThumbnail(story.id)) }) {
                    Text(stringResource(R.string.feed_thumbnail_retry))
                }
            }
        } else if (!imageLoaded) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.feed_thumbnail_loading))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenPreview() {
    VisualFeedTaskTheme {
        FeedScreen(uiState = FeedUiState.Ready(emptyList()))
    }
}
