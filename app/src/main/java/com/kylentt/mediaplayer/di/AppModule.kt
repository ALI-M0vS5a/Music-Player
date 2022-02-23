package com.kylentt.mediaplayer.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.CONTENT_TYPE_MUSIC
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceFactory
import com.kylentt.mediaplayer.domain.presenter.ServiceConnectorImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideServiceConnector(
        @ApplicationContext context: Context
    ) = ServiceConnectorImpl(context)

}

@Module
@InstallIn(ServiceComponent::class)
object serviceModule {

    @ServiceScoped
    @Provides
    fun provideAudioAttr() = AudioAttributes.Builder()
        .setContentType(CONTENT_TYPE_MUSIC)
        .setUsage(USAGE_MEDIA)
        .build()

    @ServiceScoped
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        attr: AudioAttributes
    ) = ExoPlayer.Builder(context)
        .setAudioAttributes(attr, true)
        .build()
}