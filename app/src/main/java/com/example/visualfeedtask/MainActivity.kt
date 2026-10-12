package com.example.visualfeedtask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.visualfeedtask.ui.feed.FeedRoute
import com.example.visualfeedtask.ui.theme.VisualFeedTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisualFeedTaskTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FeedRoute(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}