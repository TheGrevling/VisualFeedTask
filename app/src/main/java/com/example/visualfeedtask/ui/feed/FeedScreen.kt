package com.example.visualfeedtask.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visualfeedtask.R
import com.example.visualfeedtask.ui.theme.VisualFeedTaskTheme

@Composable
fun FeedRoute(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun FeedScreen(uiState: FeedUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feed_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        when (uiState) {
            FeedUiState.Loading -> CircularProgressIndicator()
            is FeedUiState.Ready -> {
                Text(text = stringResource(R.string.feed_foundation_message))
                Text(text = stringResource(R.string.feed_cached_count, uiState.stories.size))
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
