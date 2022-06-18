package com.kylentt.mediaplayer.domain.mediasession.service.sessions

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import com.kylentt.mediaplayer.app.delegates.AppDelegate
import com.kylentt.mediaplayer.app.dependency.AppModule
import com.kylentt.mediaplayer.core.coroutines.AppDispatchers
import com.kylentt.mediaplayer.core.media3.MediaItemFactory
import com.kylentt.mediaplayer.domain.mediasession.service.MusicLibraryService
import com.kylentt.mediaplayer.domain.mediasession.service.MusicLibraryService.Companion
import com.kylentt.mediaplayer.domain.mediasession.service.OnChanged
import com.kylentt.mediaplayer.helper.Preconditions.checkArgument
import com.kylentt.mediaplayer.helper.Preconditions.checkMainThread
import com.kylentt.mediaplayer.helper.Preconditions.checkState
import com.kylentt.mediaplayer.ui.activity.mainactivity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

class MusicLibrarySessionManager(
	private val musicService: MusicLibraryService,
	private val sessionCallback: MediaLibrarySession.MediaLibrarySessionCallback
) {

	private val sessionLock = Any()
	private val mainHandler = Handler(Looper.getMainLooper())

	private val sessionRegistry = SessionRegistry()
	private val sessionManagerJob = SupervisorJob(musicService.serviceJob)

	var isReleased = false
		private set

	val baseContext
		get() = musicService.baseContext

	val coroutineDispatchers: AppDispatchers = AppModule.provideAppDispatchers()

	val mainImmediateScope: CoroutineScope =
		CoroutineScope(coroutineDispatchers.mainImmediate + sessionManagerJob)

	val mainScope: CoroutineScope =
		CoroutineScope(coroutineDispatchers.main + sessionManagerJob)

	val isDependencyInjected
		get() = musicService.isDependencyInjected

	val lifecycle
		get() = musicService.lifecycle

	val serviceEventSF
		get() = musicService.serviceEventSF

	private fun initializeSessionImpl(service: MusicLibraryService, player: Player) {
		val get = sessionRegistry.buildMediaLibrarySession(service, player)
		sessionRegistry.changeLocalLibrarySession(get)
	}

	@MainThread
	fun initializeSession(service: MusicLibraryService, player: Player) {
		checkMainThread()
		if (isReleased) return

		if (sessionRegistry.isLibrarySessionInitialized) {
			return Timber.w("Tried to Initialize LibrarySession Multiple Times")
		}

		initializeSessionImpl(service, player)
	}

	private fun releaseImpl(obj: Any) {
		Timber.d("SessionManager releaseImpl called by $obj")

		sessionManagerJob.cancel()
		sessionRegistry.release()

		isReleased = true
	}

	private fun immediatePost(handler: Handler, block: () -> Unit) {
		if (Looper.myLooper() === handler.looper) block() else handler.postAtFrontOfQueue(block)
	}

	fun release(obj: Any) {
		if (!this.isReleased) {
			releaseImpl(obj)
		}
	}

	fun getCurrentMediaSession(): MediaSession? {
		if (isReleased || !sessionRegistry.isLibrarySessionInitialized) return null

		return sessionRegistry.localLibrarySession
	}

	fun getSessionPlayer(): Player? {
		if (isReleased || !sessionRegistry.isLibrarySessionInitialized) return null

		return sessionRegistry.localLibrarySession.player
	}

	fun changeSessionPlayer(player: Player, release: Boolean) {
		if (isReleased) return

		sessionRegistry.changeSessionPlayer(player, release)
	}

	fun registerPlayerChangedListener(onChanged: OnChanged<Player>) {
		if (isReleased) return

		sessionRegistry.registerOnPlayerChangedListener(onChanged)
	}

	fun unregisterPlayerChangedListener(onChanged: OnChanged<Player>): Boolean {
		if (isReleased) return false

		return sessionRegistry.unRegisterOnPlayerChangedListener(onChanged)
	}

	fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession? {
		return if (!isReleased) sessionRegistry.localLibrarySession else null
	}

	private inner class LocalMediaItemFiller : MediaSession.MediaItemFiller {
		override fun fillInLocalConfiguration(
			session: MediaSession,
			controller: ControllerInfo,
			mediaItem: MediaItem
		): MediaItem {

			val uri = mediaItem.mediaMetadata.mediaUri

			if (uri == null) {
				Timber.e("MediaItem mediaMetadata.mediaUri should not be null")
				return MediaItemFactory.EMPTY
			}

			return MediaItemFactory.fillInLocalConfig(mediaItem, uri)
		}
	}

	private inner class SessionRegistry {

		lateinit var localLibrarySession: MediaLibrarySession
			private set

		private val onLibrarySessionChangedListener: MutableList<OnChanged<MediaLibrarySession>> =
			mutableListOf()

		private val onPlayerChangedListener: MutableList<OnChanged<Player>> =
			mutableListOf()

		/**
		 * MediaLibrarySession status will be tracked manually as the library didn't provide one
		 */

		var isLibrarySessionReleased = false
			private set

		var isLibrarySessionInitialized = false
			get() = ::localLibrarySession.isInitialized
			private set

		fun changeLocalLibrarySession(session: MediaLibrarySession) {
			var oldSession: MediaLibrarySession? = null
			var oldPlayer: Player? = null

			if (isLibrarySessionInitialized) {
				if (localLibrarySession === session) {
					return Timber.w("Tried to change LocalLibrarySession to same Instance." +
						"\n $localLibrarySession === $session")
				}

				oldSession = localLibrarySession
				if (oldSession.player !== session.player) oldPlayer = oldSession.player
				if (!isLibrarySessionReleased) oldSession.release()
			}

			localLibrarySession = session
			onLibrarySessionChangedListener.forEach { it.onChanged(oldSession, session) }

			if (session.player !== oldPlayer) {
				onPlayerChangedListener.forEach { it.onChanged(oldPlayer, session.player) }
			}
		}

		fun changeSessionPlayer(player: Player, release: Boolean) {
			val get = localLibrarySession.player

			if (player === get) return
			if (release) get.release()

			localLibrarySession.player = player
			onPlayerChangedListener.forEach { it.onChanged(get, player) }
		}

		fun registerOnLibrarySessionChangedListener(listener: OnChanged<MediaLibrarySession>) {
			onLibrarySessionChangedListener.add(listener)
		}

		fun registerOnPlayerChangedListener(listener: OnChanged<Player>) {
			onPlayerChangedListener.add(listener)
		}

		fun unRegisterOnLibrarySessionChangedListener(listener: OnChanged<MediaLibrarySession>): Boolean {
			return onLibrarySessionChangedListener.removeAll { it === listener }
		}

		fun unRegisterOnPlayerChangedListener(listener: OnChanged<Player>): Boolean {
			return onPlayerChangedListener.removeAll { it === listener }
		}

		fun buildMediaLibrarySession(
			musicService: MusicLibraryService,
			player: Player
		): MediaLibrarySession {
			checkNotNull(musicService.baseContext)

			val intent = AppDelegate.launcherIntent()
			val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			val requestCode = MainActivity.Constants.LAUNCH_REQUEST_CODE
			val sessionActivity = PendingIntent.getActivity(musicService.baseContext, requestCode, intent, flag)
			val builder = MediaLibrarySession.Builder(musicService, player, sessionCallback)

			val get = with(builder) {
				setId(MusicLibraryService.Constants.SESSION_ID)
				setSessionActivity(sessionActivity)
				setMediaItemFiller(LocalMediaItemFiller())
				build()
			}

			return get
		}

		fun releaseSession() {
			localLibrarySession.release()
			isLibrarySessionReleased = true
		}

		fun release() {
			onLibrarySessionChangedListener.clear()
			onPlayerChangedListener.clear()
			releaseSession()
		}
	}
}
