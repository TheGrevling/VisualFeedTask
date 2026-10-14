package com.example.visualfeedtask.core.di

import android.util.Log
import com.example.visualfeedtask.BuildConfig
import com.example.visualfeedtask.core.network.HttpClient
import com.example.visualfeedtask.core.network.ConnectivityNetworkMonitor
import com.example.visualfeedtask.core.network.HttpClientImpl
import com.example.visualfeedtask.core.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun bindHttpClient(client: HttpClientImpl): HttpClient

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(monitor: ConnectivityNetworkMonitor): NetworkMonitor

    companion object {
        @Provides
        @Singleton
        fun provideHttpLogger(): HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger { message ->
            // Logcat truncates long entries, including the Top stories JSON array.
            message.chunked(3000).forEach { Log.d("HTTPLOGGER", it) }
        }

        @Provides
        @Singleton
        fun provideHttpLoggingInterceptor(
            logger: HttpLoggingInterceptor.Logger,
        ): HttpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()
    }
}
