package com.example.visualfeedtask.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.visualfeedtask.ui.theme.VisualFeedTaskTheme
import org.junit.Rule
import org.junit.Test

class FeedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyCacheShowsFoundationScreen() {
        composeRule.setContent {
            VisualFeedTaskTheme {
                FeedScreen(FeedUiState.Ready(emptyList()))
            }
        }

        composeRule.onNodeWithText("Hacker News Visual Feed").assertIsDisplayed()
        composeRule.onNodeWithText("Cached stories: 0").assertIsDisplayed()
    }
}
