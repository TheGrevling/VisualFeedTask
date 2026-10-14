package com.example.visualfeedtask.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.mutableStateOf
import com.example.visualfeedtask.ui.theme.VisualFeedTaskTheme
import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.model.StoryFeedType
import com.example.visualfeedtask.ui.feed.models.formatSubmissionTime
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Rule
import org.junit.Test
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.util.UUID

class FeedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyFeedShowsEmptyMessage() {
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(FeedUiState.Ready(emptyList()))
            }
        }

        composeRule.onNodeWithText("Hacker News Visual Feed").assertIsDisplayed()
        composeRule.onNodeWithText("No stories to display.").assertIsDisplayed()
    }

    @Test
    fun feedTypeButtonsDefaultToTopAndSendSelection() {
        val intents = mutableListOf<FeedIntent>()
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(FeedUiState.Ready(emptyList()), onIntent = { intents += it })
            }
        }

        composeRule.onNodeWithText("Top stories").assertIsSelected()
        composeRule.onNodeWithText("New stories").assertIsNotSelected().performClick()

        assertEquals(listOf(FeedIntent.SelectFeed(StoryFeedType.NEW)), intents)
    }

    @Test
    fun offlineFeedWithoutSavedStoriesExplainsEmptinessAndOffersRetry() {
        val intents = mutableListOf<FeedIntent>()
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(FeedUiState.Ready(emptyList(), isOffline = true), onIntent = { intents += it })
            }
        }

        composeRule.onNodeWithText(
            "You're offline and no stories have been saved yet. Connect to the internet to load stories.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf<FeedIntent>(FeedIntent.Retry), intents)
    }

    @Test
    fun pullingDownRequestsRefresh() {
        val intents = mutableListOf<FeedIntent>()
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(
                    FeedUiState.Ready(listOf(Story(1, "Kotlin news", "alice", 42, 100, null, null))),
                    onIntent = { intents += it },
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertTrue(FeedIntent.Refresh in intents)
    }

    @Test
    fun offlineFeedDisplaysSavedStory() {
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(
                    FeedUiState.Ready(
                        listOf(Story(1, "Kotlin news", "alice", 42, 100, null, null)),
                        isOffline = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Network unavailable. Showing saved stories.").assertIsDisplayed()
        composeRule.onNodeWithText("Kotlin news").assertIsDisplayed()
        composeRule.onNodeWithText("By alice - 42 points").assertIsDisplayed()
        composeRule.onNodeWithText("Submitted ${formatSubmissionTime(100)}").assertIsDisplayed()
    }

    @Test
    fun errorShowsRetryAction() {
        val intents = mutableListOf<FeedIntent>()
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(FeedUiState.Error, onIntent = intents::add)
            }
        }

        composeRule.onNodeWithText("Unable to load stories. Please try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(listOf(FeedIntent.Retry), intents)
    }

    @Test
    fun visibleStoryRequestsThumbnailAndDisplaysProgress() {
        val intents = mutableListOf<FeedIntent>()
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(
                    FeedUiState.Ready(
                        listOf(Story(7, "Story", "alice", 42, 100, null, null)),
                        loadingThumbnails = setOf(7),
                    ),
                    onIntent = intents::add,
                )
            }
        }
        composeRule.onNodeWithText("Generating thumbnail...").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(FeedIntent.RequestThumbnail(7)), intents)
        }
    }

    @Test
    fun disabledGenerationShowsDefaultThumbnailWithoutRequests() {
        val intents = mutableListOf<FeedIntent>()
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(
                    FeedUiState.Ready(
                        listOf(Story(7, "Story", "alice", 42, 100, null, null)),
                        imageGenerationEnabled = false,
                    ),
                    onIntent = intents::add,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Default thumbnail").assertIsDisplayed()
        composeRule.onNodeWithText("Generating thumbnail...").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(emptyList<FeedIntent>(), intents) }
    }

    @Test
    fun queuedThumbnailShowsQueuedWithoutGeneratingProgress() {
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(
                    FeedUiState.Ready(
                        listOf(Story(7, "Story", "alice", 42, 100, null, null)),
                        queuedThumbnails = setOf(7),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Queued").assertIsDisplayed()
        composeRule.onNodeWithText("Generating thumbnail...").assertDoesNotExist()
    }

    @Test
    fun failedThumbnailHasIndependentRetry() {
        val intents = mutableListOf<FeedIntent>()
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(
                    FeedUiState.Ready(
                        listOf(Story(7, "Story", "alice", 42, 100, null, null)),
                        failedThumbnails = setOf(7),
                    ),
                    onIntent = intents::add,
                )
            }
        }
        composeRule.onNodeWithText("Thumbnail unavailable.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry thumbnail").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(FeedIntent.RequestThumbnail(7), FeedIntent.RetryThumbnail(7)), intents,
            )
        }
    }

    @Test
    fun cachedLocalThumbnailIsDisplayedWithoutProgress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "thumbnail-test-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
            composeRule.setContent {
                VisualFeedTaskTheme {
                    FeedScreen(
                        FeedUiState.Ready(
                            listOf(Story(7, "Story", "alice", 42, 100, null, file.absolutePath)),
                        ),
                    )
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(
                    androidx.compose.ui.test.hasText("Generating thumbnail..."),
                ).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithContentDescription("Illustration for Story").assertIsDisplayed()
            composeRule.onNodeWithText("Generating thumbnail...").assertDoesNotExist()
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    @Test
    fun regeneratedThumbnailReloadsWhenItsPathHasNotChanged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "thumbnail-test-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val story = Story(7, "Story", "alice", 42, 100, null, file.absolutePath)
        val state = mutableStateOf(
            FeedUiState.Ready(listOf(story), loadingThumbnails = setOf(7)),
        )
        val intents = mutableListOf<FeedIntent>()
        try {
            file.writeText("corrupt image")
            composeRule.setContent {
                VisualFeedTaskTheme {
                    FeedScreen(state.value, onIntent = intents::add)
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                intents.contains(FeedIntent.ThumbnailLoadFailed(7))
            }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
            composeRule.runOnIdle {
                state.value = FeedUiState.Ready(listOf(story))
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText("Generating thumbnail..."))
                    .fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithContentDescription("Illustration for Story").assertIsDisplayed()
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    @Test
    fun scrollingToLastStoryEmitsReachedBottom() {
        val intents = mutableListOf<FeedIntent>()
        val stories = (1L..25L).map { id ->
            Story(id, "Story $id", "alice", 42, 100, null, null)
        }
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(
                    FeedUiState.Ready(stories, hasMore = true),
                    onIntent = intents::add,
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(emptyList<FeedIntent>(), intents.filter { it == FeedIntent.ReachedBottom })
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Story 25"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            intents.contains(FeedIntent.ReachedBottom)
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(FeedIntent.ReachedBottom), intents.filter { it == FeedIntent.ReachedBottom },
            )
        }
    }
}
