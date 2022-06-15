package com.kylentt.mediaplayer.domain.mediasession.service.event

import androidx.media3.common.Player
import com.kylentt.mediaplayer.app.dependency.AppModule
import com.kylentt.mediaplayer.domain.mediasession.service.MusicLibraryService
import com.kylentt.mediaplayer.domain.mediasession.service.OnChanged
import com.kylentt.mediaplayer.domain.mediasession.service.notification.MusicLibraryNotificationProvider
import com.kylentt.mediaplayer.domain.mediasession.service.sessions.MusicLibrarySessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MusicLibraryEventManager(
	private val musicService: MusicLibraryService,
	private val sessionManager: MusicLibrarySessionManager,
	notificationProvider: MusicLibraryNotificationProvider
) {

	private val eventHandler: MusicLibraryEventHandler
	private val eventListener: MusicLibraryEventListener

	val appDispatchers = AppModule.provideAppDispatchers()

	val baseContext
		get() = musicService.baseContext

	val eventSF
		get() = musicService.serviceEventSF

	val sessionPlayer
		get() = sessionManager.getSessionPlayer()

	val eventJob = SupervisorJob(musicService.serviceJob)

	val immediateScope: CoroutineScope = CoroutineScope(appDispatchers.mainImmediate + eventJob)
	val mainScope: CoroutineScope = CoroutineScope(appDispatchers.main + eventJob)
	val ioScope: CoroutineScope = CoroutineScope(appDispatchers.io + eventJob)

	init {
		eventHandler = MusicLibraryEventHandler(this, notificationProvider)
		eventListener = MusicLibraryEventListener(this, sessionManager, eventHandler)
	}

	fun startListener() {
		eventListener.start(stopSelf = true, releaseSelf = true)
	}

	fun stopListener() {
		eventListener.stop()
	}

	fun releaseListener(obj: Any) {
		eventListener.release(obj)
	}

	fun registerOnPlayerChangedListener(onChanged: OnChanged<Player>) {
		sessionManager.registerPlayerChangedListener(onChanged)
	}

	fun unregisterOnPlayerChangedListener(onChanged: OnChanged<Player>): Boolean {
		return sessionManager.unregisterPlayerChangedListener(onChanged)
	}
}