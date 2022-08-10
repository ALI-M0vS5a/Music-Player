package com.kylentt.mediaplayer.domain.viewmodels

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.kylentt.mediaplayer.helper.external.IntentWrapper
import com.kylentt.mediaplayer.helper.external.MediaIntentHandler
import com.kylentt.mediaplayer.helper.image.CoilHelper
import com.kylentt.musicplayer.common.android.environment.DeviceInfo
import com.kylentt.musicplayer.common.android.memory.maybeWaitForMemory
import com.kylentt.musicplayer.common.coroutines.CoroutineDispatchers
import com.kylentt.musicplayer.common.coroutines.safeCollect
import com.kylentt.musicplayer.common.kotlin.coroutine.checkCancellation
import com.kylentt.musicplayer.core.app.AppDelegate
import com.kylentt.musicplayer.domain.musiclib.MusicLibrary
import com.kylentt.musicplayer.domain.musiclib.core.media3.mediaitem.MediaItemHelper
import com.kylentt.musicplayer.ui.main.compose.screens.root.PlaybackControlModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@HiltViewModel
class MediaViewModel @Inject constructor(
	private val coilHelper: CoilHelper,
	private val deviceInfo: DeviceInfo,
	private val dispatchers: CoroutineDispatchers,
	private val itemHelper: MediaItemHelper,
	private val intentHandler: MediaIntentHandler
) : ViewModel() {

	private val cacheManager = AppDelegate.cacheManager

	private val ioScope = viewModelScope + dispatchers.io
	private val computationScope = viewModelScope + dispatchers.computation

	private val positionStateFlow = MusicLibrary.localAgent.session.info.playbackPosition

	private val player = MusicLibrary.localAgent.session.player

	private var positionCollectorJob = Job().job
	private var updateArtJob = Job().job

	val playbackControlModel = PlaybackControlModel()

	init {
		viewModelScope.launch(dispatchers.main) {
			collectPlaybackState()
		}
		viewModelScope.launch(dispatchers.main) {
			positionStateFlow.safeCollect { playbackControlModel.updatePosition(it) }
		}
	}

	fun play() = player.play()
	fun pause() = player.pause()

  fun handleMediaIntent(intent: IntentWrapper) {
    viewModelScope.launch(dispatchers.computation) {
			if (intent.shouldHandleIntent) intentHandler.handleMediaIntent(intent)
		}
  }

  private suspend fun collectPlaybackState() {
    MusicLibrary.localAgent.session.info.playbackState.safeCollect { playbackState ->
			Timber.d("collectPlaybackState")

			val get = playbackControlModel.mediaItem

			if (get !== playbackState.mediaItem) {
				playbackControlModel.updateArt(null)
				dispatchUpdateItemBitmap(playbackState.mediaItem)
			}

			playbackControlModel.updateBy(playbackState)
    }
  }

  @OptIn(ExperimentalTime::class)
	@MainThread
  suspend fun dispatchUpdateItemBitmap(item: MediaItem) {
		updateArtJob.cancel()
    updateArtJob = ioScope.launch {
			ensureActive()

			maybeWaitForMemory(1.5F, 2000, 500, deviceInfo) {
				Timber.w("dispatchUpdateItemBitmap will wait due to low memory")
			}

			try {

				Timber.d("itemHasExtra: ${item.mediaMetadata.extras}")

				val bitmap = item.mediaMetadata.extras?.getString("cachedArtwork")?.let { file ->
					coilHelper.loadBitmap(File(file), 500 ,500)
				} ?: itemHelper.getEmbeddedPicture(item)?.let { bytes ->
					coilHelper.loadBitmap(bytes, 500 ,500)
				}


				checkCancellation {
					bitmap?.recycle()
				}

				withContext(dispatchers.main) {
					playbackControlModel.updateArt(bitmap)
				}

			} catch (_: OutOfMemoryError) {}
		}
	}
}
