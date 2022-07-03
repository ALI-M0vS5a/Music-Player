package com.kylentt.mediaplayer.domain.mediasession.libraryservice

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.kylentt.mediaplayer.app.delegates.AppDelegate
import com.kylentt.mediaplayer.app.dependency.AppModule
import com.kylentt.mediaplayer.core.coroutines.AppDispatchers
import com.kylentt.mediaplayer.core.coroutines.safeCollect
import com.kylentt.mediaplayer.core.extenstions.forEachClear
import com.kylentt.mediaplayer.domain.mediasession.MediaSessionConnector
import com.kylentt.mediaplayer.domain.mediasession.libraryservice.notification.MediaNotificationManager
import com.kylentt.mediaplayer.domain.mediasession.libraryservice.playback.PlaybackManager
import com.kylentt.mediaplayer.domain.mediasession.libraryservice.sessions.SessionManager
import com.kylentt.mediaplayer.domain.mediasession.libraryservice.sessions.SessionProvider
import com.kylentt.mediaplayer.domain.mediasession.libraryservice.state.StateManager
import com.kylentt.mediaplayer.domain.service.ContextBroadcastManager
import com.kylentt.mediaplayer.helper.Preconditions.checkMainThread
import com.kylentt.mediaplayer.helper.Preconditions.checkState
import com.kylentt.mediaplayer.helper.VersionHelper
import com.kylentt.mediaplayer.ui.activity.mainactivity.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.system.exitProcess

@AndroidEntryPoint
class MusicLibraryService : MediaLibraryService() {


	@Inject lateinit var injectedExoPlayer: ExoPlayer
	@Inject lateinit var injectedSessionConnector: MediaSessionConnector
	private lateinit var notificationManagerService: NotificationManager
	private lateinit var broadcastManager: ContextBroadcastManager

	private var mReleasing = false

	private val appDispatchers = AppModule.provideAppDispatchers()
	private val serviceJob = SupervisorJob()
	private val serviceScope = CoroutineScope(appDispatchers.main + serviceJob)

	private val stateRegistry = StateRegistry()
	private val componentManager = ComponentManager()

	private val sessionManager = SessionManager(SessionProvider(this))
	private val stateManager = StateManager(StateInteractor())
	private val notificationManager = MediaNotificationManager(MediaNotificationId)
	private val playbackManager = PlaybackManager()

	private val currentState
		get() = stateRegistry.serviceStateSF.value

	private val isServiceCreated get() = currentState >= ServiceState.Created
	private val isServiceStarted get() = currentState >= ServiceState.Started
	private val isServiceForeground get() = currentState == ServiceState.Foreground
	private val isServicePaused get() = currentState <= ServiceState.Paused
	private val isServiceStopped get() = currentState <= ServiceState.Stopped
	private val isServiceReleased get() = currentState <= ServiceState.Released
	private val isServiceDestroyed get() = currentState == ServiceState.Destroyed

	init {
		componentManager.add(sessionManager)
		componentManager.add(stateManager)
		componentManager.add(playbackManager)
		componentManager.add(notificationManager)
		onPostInitialize()
	}

	private fun onPostInitialize() {
		stateRegistry.onEvent(ServiceEvent.Initialize, true)
		componentManager.start()
	}

	override fun onCreate() {
		onContextAttached()
		super.onCreate()
		onDependencyInjected()
		postCreate()
	}

	private fun onContextAttached() {
		broadcastManager = ContextBroadcastManager(this)
		notificationManagerService = getSystemService(NotificationManager::class.java)
		stateRegistry.onEvent(ServiceEvent.AttachContext, true)
	}

	private fun onDependencyInjected() {
		stateRegistry.onEvent(ServiceEvent.InjectDependency, true)
	}

	private fun postCreate() {
		stateRegistry.onEvent(ServiceEvent.Create, true)
	}

	private fun onStart() {
		stateRegistry.onEvent(ServiceEvent.Start, true)
	}

	override fun onBind(intent: Intent?): IBinder? {
		if (!isServiceStarted) onStart()
		return super.onBind(intent)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (!isServiceStarted) onStart()
		return super.onStartCommand(intent, flags, startId)
	}

	private fun startForegroundService(notification: Notification) {
		if (!isServiceStarted) onStart()
		if (VersionHelper.hasQ()) {
			startForeground(MediaNotificationId, notification,
				ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
		} else {
			startForeground(MediaNotificationId, notification)
		}
		if (!isServiceForeground) {
			stateRegistry.onEvent(ServiceEvent.ResumeForeground, true)
		}
	}

	private fun stopForegroundService(removeNotification: Boolean) {
		stopForeground(removeNotification)
		if (removeNotification) notificationManagerService.cancel(MediaNotificationId)
		if (isServiceForeground) {
			stateRegistry.onEvent(ServiceEvent.PauseForeground, true)
		}
	}

	private fun stopService(release: Boolean) {
		if (release) mReleasing = true
		if (isServiceForeground) stopForeground(release)
		stopSelf()

		stateRegistry.onEvent(ServiceEvent.Stop(release), true)
		if (release) releaseService()
	}

	private fun releaseService() {
		if (!isServiceStopped) stopService(true)
		stateRegistry.onEvent(ServiceEvent.Release, true)
		releaseComponent()
		releaseSessions()
		serviceJob.cancel()
	}

	private fun releaseComponent() {
		broadcastManager.release()
		componentManager.release()
		injectedExoPlayer.release()
	}

	private fun releaseSessions() {
		injectedSessionConnector.disconnectService()
		sessions.forEachClear { it.release() }
	}

	override fun onDestroy() {
		if (!isServiceReleased) releaseService()
		super.onDestroy()
		postDestroy()
	}

	private fun postDestroy() {
		stateRegistry.onEvent(ServiceEvent.Destroy, true)
		checkState(componentManager.released)


		if (!MainActivity.isAlive) {
			// could Leak
			// TODO: CleanUp
			notificationManagerService.cancelAll()
			exitProcess(0)
		}
	}

	override fun onUpdateNotification(session: MediaSession) {
		notificationManager.onUpdateNotification(session)
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
		return sessionManager.onGetSession(controllerInfo)
	}

	abstract class ServiceComponent {
		private var mCreated = false
		private var mStarted = false
		private var mReleased = false
		private var mContextNotified = false
		private var mDependencyNotified = false

		private lateinit var mServiceDelegate: ServiceDelegate
		private lateinit var mComponentDelegate: ComponentDelegate

		protected open val serviceDelegate
			get() = mServiceDelegate

		protected open val componentDelegate
			get() = mComponentDelegate

		open val isCreated
			get() = mCreated

		open val isStarted
			get() = mStarted

		open val isReleased
			get() = mReleased

		@MainThread
		fun createComponent(libraryService: MusicLibraryService) {
			if (!checkMainThread() || mCreated || mReleased) return
			create(libraryService.ServiceDelegate())
			checkState(::mServiceDelegate.isInitialized) {
				"ServiceComponent did not call super.create(): $this"
			}
			mCreated = true
		}

		@MainThread
		fun startComponent(libraryService: MusicLibraryService) {
			if (!checkMainThread() || mStarted || mReleased) return
			if (!mCreated) createComponent(libraryService)
			if (!mContextNotified) notifyContextAttached(libraryService)
			if (!mDependencyNotified) notifyDependencyInjected(libraryService)

			start(libraryService.ComponentDelegate())
			checkState(::mComponentDelegate.isInitialized) {
				"ServiceComponent did not call super.start(): $this"
			}
			mStarted = true
		}

		@MainThread
		fun releaseComponent() {
			if (!checkMainThread() || mReleased) return
			release()
			mReleased = true
		}

		@MainThread
		fun notifyContextAttached(service: MusicLibraryService) {
			checkMainThread()
			if (mContextNotified || isReleased) return
			if (!isCreated) createComponent(service)
			serviceContextAttached(service)
		}

		@MainThread
		fun notifyDependencyInjected(service: MusicLibraryService) {
			checkMainThread()
			if (mDependencyNotified || isReleased) return
			if (!isCreated) createComponent(service)
			serviceDependencyInjected()
		}

		@MainThread
		@CallSuper
		protected open fun create(serviceDelegate: ServiceDelegate) {
			mServiceDelegate = serviceDelegate
		}

		@MainThread
		@CallSuper
		protected open fun start(componentDelegate: ComponentDelegate) {
			mComponentDelegate = componentDelegate
		}

		@MainThread
		protected open fun release() {}

		@MainThread
		@CallSuper
		protected open fun serviceContextAttached(context: Context) {
			mContextNotified = true
		}

		@MainThread
		@CallSuper
		protected open fun serviceDependencyInjected() {
			mDependencyNotified = true
		}

		abstract class Interactor
	}

	inner class StateInteractor {
		val isForeground
			get() = isServiceForeground

		fun startForeground(notification: Notification) {
			if (isServiceCreated) startForegroundService(notification)
		}

		fun stopForeground(removeNotification: Boolean) {
			if (isServiceCreated) stopForegroundService(removeNotification)
		}

		fun start() {
			if (isServiceCreated && !isServiceStarted) onStart()
		}

		fun stop(release: Boolean) {
			if (isServiceCreated) stopService(release)
		}

		fun release() {
			if (!isServiceReleased) releaseService()
		}
	}

	inner class ServiceDelegate {
		val propertyInteractor = PropertyInteractor()
	}

	inner class PropertyInteractor {
		val context: Context? get() = baseContext
		val injectedPlayer: ExoPlayer get() = injectedExoPlayer

		val serviceDispatchers
			get() = appDispatchers
		val serviceMainJob
			get() = serviceJob
		val sessionConnector
			get() = injectedSessionConnector
	}

	inner class ComponentDelegate {
		val sessionInteractor
			get() = sessionManager.interactor

		val stateInteractor
			get() = stateManager.interactor

		val notificationInteractor
			get() = notificationManager.interactor
	}

	// Don't Implement LifecycleOwner when there's no use case yet
	private inner class StateRegistry {
		private val mutableServiceStateSF = MutableStateFlow<ServiceState>(ServiceState.Nothing)
		private val mutableServiceEventSF = MutableStateFlow<ServiceEvent>(ServiceEvent.Nothing)

		val serviceStateSF = mutableServiceStateSF.asStateFlow()
		val serviceEventSF = mutableServiceEventSF.asStateFlow()

		fun onEvent(event: ServiceEvent, updateState: Boolean) {

			Timber.d("StateRegistry onEvent\nevent: $event")

			if (event is ServiceEvent.SingleTimeEvent) {
				checkState(!event.consumed)
				event.consume()
			}

			dispatchEvent(event)
			if (updateState) updateState(event.resultState)
		}

		private fun dispatchEvent(event: ServiceEvent) {
			mutableServiceEventSF.value = event
		}

		fun updateState(state: ServiceState) {
			val currentState = serviceStateSF.value
			checkState(state upFrom currentState || state downFrom currentState) {
				if (state == currentState) {
					"ServiceState updated multiple times $currentState"
				} else {
					"ServiceState Jump attempt from $currentState to $state"
				}
			}

			when (state) {
				ServiceState.Nothing -> throw IllegalArgumentException()
				ServiceState.Initialized -> checkState(currentState == ServiceState.Nothing)
				else -> Unit
			}

			StateDelegate.updateState(this, state)
			mutableServiceStateSF.value = state
		}
	}

	private inner class ComponentManager {
		private val components = mutableSetOf<ServiceComponent>()

		// Explicit reference
		private val service = this@MusicLibraryService

		var started = false
			private set

		var released = false
			private set

		@MainThread
		fun add(component: ServiceComponent) {
			if (!checkMainThread() || released) return
			components.add(component)
			notifyComponent(component, service.currentState)
		}

		@MainThread
		fun remove(component: ServiceComponent) {
			if (!checkMainThread() || released) return
			components.remove(component)
			component.releaseComponent()
		}

		@MainThread
		fun start() {
			if (!checkMainThread() || started || released) return
			serviceScope.launch {
				stateRegistry.serviceEventSF.safeCollect { event ->
					components.forEach { notifyComponent(it, event.resultState) }
				}
			}
		}

		@MainThread
		fun release() {
			if (!checkMainThread() || released) return
			components.forEachClear { it.releaseComponent() }
			released = true
		}

		private fun notifyComponent(component: ServiceComponent, state: ServiceState) {
			when(state) {
				ServiceState.Nothing -> Unit
				ServiceState.Initialized -> component.createComponent(service)
				ServiceState.ContextAttached -> component.notifyContextAttached(service)
				ServiceState.DependencyInjected -> component.notifyDependencyInjected(service)
				ServiceState.Created, ServiceState.Stopped, ServiceState.Started, ServiceState.Paused,
					ServiceState.Foreground -> component.startComponent(service)
				ServiceState.Released, ServiceState.Destroyed -> component.releaseComponent()
			}
		}
	}

	object StateDelegate : ReadOnlyProperty <Any?, ServiceState> {
		private var savedState: ServiceState = ServiceState.Nothing
		private var stateProvider: Any? = null
		fun updateState(holder: Any, state: ServiceState) {
			checkState(state upFrom savedState || state downFrom savedState) {
				"StateDelegate StateJump Attempt from $savedState to $state"
			}
			when (state) {
				ServiceState.Nothing -> throw IllegalArgumentException()
				ServiceState.Initialized -> {
					checkState(holder !== stateProvider)
					stateProvider = holder
				}
				else -> checkState(stateProvider === holder)
			}
			savedState = state
		}
		override fun getValue(thisRef: Any?, property: KProperty<*>): ServiceState = savedState
	}

	sealed class ServiceState : Comparable<ServiceState> {

		override fun compareTo(other: ServiceState): Int = when {
			ComparableInt.get(this) > ComparableInt.get(other) -> 1
			ComparableInt.get(this) < ComparableInt.get(other) -> -1
			else -> 0
		}

		infix fun upFrom(other: ServiceState): Boolean {
			return ComparableInt.get(this) == (ComparableInt.get(other) - 1)
		}

		infix fun downFrom(other: ServiceState): Boolean {
			return ComparableInt.get(this) == (ComparableInt.get(other) + 1)
		}

		object Nothing : ServiceState()
		object Initialized : ServiceState()
		object ContextAttached : ServiceState()
		object DependencyInjected : ServiceState()
		object Created : ServiceState()
		object Started : ServiceState()
		object Foreground : ServiceState()
		object Paused : ServiceState()
		object Stopped : ServiceState()
		object Released : ServiceState()
		object Destroyed : ServiceState()

		object ComparableInt {
			private const val NOTHING = -1
			private const val INITIALIZED = 0
			private const val CONTEXT_ATTACHED = 1
			private const val DEPENDENCY_INJECTED = 2
			private const val CREATED = 3
			private const val STARTED = 4
			private const val FOREGROUND = 5
			private const val PAUSED = 4
			private const val STOPPED = 3
			private const val Released = 2
			private const val Destroyed = 1

			fun get(serviceState: ServiceState): Int = when (serviceState) {
				ServiceState.Nothing -> NOTHING
				ServiceState.Initialized -> INITIALIZED
				ServiceState.ContextAttached -> CONTEXT_ATTACHED
				ServiceState.DependencyInjected -> DEPENDENCY_INJECTED
				ServiceState.Created -> CREATED
				ServiceState.Started -> STARTED
				ServiceState.Foreground -> FOREGROUND
				ServiceState.Paused -> PAUSED
				ServiceState.Stopped -> STOPPED
				ServiceState.Released -> Released
				ServiceState.Destroyed -> Destroyed
			}
		}
	}

	sealed class ServiceEvent {

		abstract val resultState: ServiceState

		sealed class SingleTimeEvent : ServiceEvent() {
			var consumed: Boolean = false
				private set
			fun consume() {
				consumed = true
			}
		}

		interface LifecycleEvent {
			fun asLifecycleEvent(): Lifecycle.Event
		}

		object Nothing : SingleTimeEvent() {
			override val resultState: ServiceState = ServiceState.Nothing
		}
		object Initialize : SingleTimeEvent() {
			override val resultState: ServiceState = ServiceState.Initialized
		}
		object AttachContext : SingleTimeEvent() {
			override val resultState: ServiceState = ServiceState.ContextAttached
		}
		object InjectDependency : SingleTimeEvent() {
			override val resultState: ServiceState = ServiceState.DependencyInjected
		}
		object Create : SingleTimeEvent(), LifecycleEvent {
			override val resultState: ServiceState = ServiceState.Created
			override fun asLifecycleEvent(): Lifecycle.Event = Lifecycle.Event.ON_CREATE
		}
		object Start : ServiceEvent(), LifecycleEvent {
			override val resultState: ServiceState = ServiceState.Started
			override fun asLifecycleEvent(): Lifecycle.Event = Lifecycle.Event.ON_START
		}
		object ResumeForeground : ServiceEvent(), LifecycleEvent {
			override val resultState: ServiceState = ServiceState.Foreground
			override fun asLifecycleEvent(): Lifecycle.Event = Lifecycle.Event.ON_RESUME
		}
		object PauseForeground : ServiceEvent(), LifecycleEvent {
			override val resultState: ServiceState = ServiceState.Paused
			override fun asLifecycleEvent(): Lifecycle.Event = Lifecycle.Event.ON_PAUSE
		}
		data class Stop(val isReleasing: Boolean) : ServiceEvent(), LifecycleEvent {
			override val resultState: ServiceState = ServiceState.Stopped
			override fun asLifecycleEvent(): Lifecycle.Event = Lifecycle.Event.ON_STOP
		}
		object Release : SingleTimeEvent() {
			override val resultState: ServiceState = ServiceState.Released
		}
		object Destroy : SingleTimeEvent(), LifecycleEvent {
			override val resultState: ServiceState = ServiceState.Destroyed
			override fun asLifecycleEvent(): Lifecycle.Event = Lifecycle.Event.ON_DESTROY
		}
	}

	companion object {
		const val MediaNotificationId = 301

		fun getComponentName(): ComponentName = AppDelegate.componentName(MusicLibraryService::class)
	}
}
