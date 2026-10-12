package com.example.visualfeedtask.data.di

import android.content.Context
import androidx.room.Room
import com.example.visualfeedtask.BuildConfig
import com.example.visualfeedtask.data.local.StoryDao
import com.example.visualfeedtask.data.local.ThumbnailDao
import com.example.visualfeedtask.data.local.VisualFeedDatabase
import com.example.visualfeedtask.data.remote.HackerNewsApi
import com.example.visualfeedtask.data.remote.HackerNewsApiImpl
import com.example.visualfeedtask.data.repository.OfflineFirstStoryRepository
import com.example.visualfeedtask.data.repository.StoryRepository
import com.example.visualfeedtask.data.repository.StoryThumbnailRepository
import com.example.visualfeedtask.data.repository.LocalStoryThumbnailRepository
import com.example.visualfeedtask.data.thumbnail.MediaPipeThumbnailGenerator
import com.example.visualfeedtask.data.thumbnail.ThumbnailGenerator
import com.google.gson.Gson
import com.google.gson.Strictness
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindStoryThumbnailRepository(
        repository: LocalStoryThumbnailRepository,
    ): StoryThumbnailRepository

    @Binds
    @Singleton
    abstract fun bindThumbnailGenerator(generator: MediaPipeThumbnailGenerator): ThumbnailGenerator

    @Binds
    @Singleton
    abstract fun bindStoryRepository(repository: OfflineFirstStoryRepository): StoryRepository

    @Binds
    @Singleton
    abstract fun bindHackerNewsApi(api: HackerNewsApiImpl): HackerNewsApi
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageGenerationDisabled

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @ImageGenerationDisabled
    fun provideImageGenerationDisabled(): Boolean = BuildConfig.DISABLE_ON_DEVICE_IMAGE_GENERATION

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VisualFeedDatabase =
        Room.databaseBuilder(context, VisualFeedDatabase::class.java, "visual-feed.db")
            .build()

    @Provides
    fun provideStoryDao(database: VisualFeedDatabase): StoryDao = database.storyDao()

    @Provides
    fun provideThumbnailDao(database: VisualFeedDatabase): ThumbnailDao = database.thumbnailDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson().newBuilder().setStrictness(Strictness.STRICT).create()
}
