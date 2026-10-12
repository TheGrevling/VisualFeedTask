package com.example.visualfeedtask.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.visualfeedtask.ui.theme.VisualFeedTaskTheme
import com.example.visualfeedtask.data.model.Story
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
            assertEquals(emptyList<FeedIntent>(), intents.toList())
        }
        composeRule.onNodeWithText("Story 25").performScrollTo()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            intents.contains(FeedIntent.ReachedBottom)
        }
        composeRule.runOnIdle {
            assertEquals(listOf(FeedIntent.ReachedBottom), intents.toList())
        }
    }
}
