package com.example.visualfeedtask.ui.feed.models

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.visualfeedtask.R
import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.ui.feed.FeedIntent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val SUBMISSION_TIME_PATTERN = "HH:mm dd/MM/yyyy"

fun formatSubmissionTime(
    epochSeconds: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String = SimpleDateFormat(SUBMISSION_TIME_PATTERN, Locale.ROOT)
    .apply { this.timeZone = timeZone }
    .format(Date(epochSeconds * 1000))

@Composable
fun StoryCard(
    story: Story,
    imageGenerationEnabled: Boolean,
    thumbnailFailed: Boolean,
    thumbnailGenerating: Boolean,
    thumbnailQueued: Boolean,
    onIntent: (FeedIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        if (imageGenerationEnabled) {
            StoryThumbnail(
                story = story,
                failed = thumbnailFailed,
                generating = thumbnailGenerating,
                queued = thumbnailQueued,
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
            Text(stringResource(R.string.feed_story_metadata, story.author, story.score))
            val submittedAt = remember(story.submittedAtEpochSeconds) {
                formatSubmissionTime(story.submittedAtEpochSeconds)
            }
            Text(
                text = stringResource(R.string.feed_story_submitted_at, submittedAt),
                style = MaterialTheme.typography.bodySmall,
            )
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
