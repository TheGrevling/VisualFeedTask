package com.example.visualfeedtask.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visualfeedtask.R
import com.example.visualfeedtask.data.model.StoryFeedType
import com.example.visualfeedtask.ui.feed.models.StoryCard
import androidx.compose.runtime.key
import com.example.visualfeedtask.ui.theme.VisualFeedTaskTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FeedRoute(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFeed by viewModel.selectedFeed.collectAsStateWithLifecycle()
    //This is a single screen project, navigation is not required.
    FeedScreen(
        uiState = uiState,
        modifier = modifier,
        selectedFeed = selectedFeed,
        onIntent = viewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    uiState: FeedUiState,
    modifier: Modifier = Modifier,
    selectedFeed: StoryFeedType = StoryFeedType.TOP,
    onIntent: (FeedIntent) -> Unit = {},
) {
    val listState = key(selectedFeed) { rememberLazyListState() }
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
        FeedTypeSelector(selectedFeed = selectedFeed, onIntent = onIntent)
        PullToRefreshBox(
            isRefreshing = ready?.isRefreshing == true,
            onRefresh = { onIntent(FeedIntent.Refresh) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when (uiState) {
                FeedUiState.Loading -> ScrollableMessage { CircularProgressIndicator() }
                FeedUiState.Error -> ScrollableMessage {
                    Text(text = stringResource(R.string.feed_load_error))
                    Button(onClick = { onIntent(FeedIntent.Retry) }) {
                        Text(text = stringResource(R.string.feed_retry))
                    }
                }
                is FeedUiState.Ready -> if (uiState.stories.isEmpty()) {
                    ScrollableMessage {
                        if (uiState.refreshFailed) {
                            Text(text = stringResource(R.string.feed_refresh_error))
                        }
                        if (uiState.isOffline) {
                            Text(
                                text = stringResource(R.string.feed_offline_empty),
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = { onIntent(FeedIntent.Retry) }) {
                                Text(text = stringResource(R.string.feed_retry))
                            }
                        } else {
                            Text(text = stringResource(R.string.feed_empty))
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (uiState.refreshFailed) {
                            item(key = "refresh-error") {
                                Text(stringResource(R.string.feed_refresh_error))
                            }
                        }
                        if (uiState.isOffline) {
                            item(key = "offline") { Text(stringResource(R.string.feed_offline)) }
                        }
                        items(uiState.stories, key = { it.id }) { story ->
                            StoryCard(
                                story = story,
                                imageGenerationEnabled = uiState.imageGenerationEnabled,
                                thumbnailFailed = story.id in uiState.failedThumbnails,
                                thumbnailGenerating = story.id in uiState.loadingThumbnails,
                                thumbnailQueued = story.id in uiState.queuedThumbnails,
                                onIntent = onIntent,
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item(key = "loading") { CircularProgressIndicator() }
                        }
                        if (uiState.loadMoreFailed) {
                            item(key = "retry") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun ScrollableMessage(content: @Composable ColumnScope.() -> Unit) {
    // Scrollable so the pull-to-refresh gesture also works on non-list states.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun FeedTypeSelector(
    selectedFeed: StoryFeedType,
    onIntent: (FeedIntent) -> Unit,
) {
    val options = listOf(
        StoryFeedType.TOP to R.string.feed_top_stories,
        StoryFeedType.NEW to R.string.feed_new_stories,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (feedType, label) ->
            SegmentedButton(
                selected = feedType == selectedFeed,
                onClick = { onIntent(FeedIntent.SelectFeed(feedType)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(label))
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
