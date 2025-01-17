package com.flammky.musicplayer.library.dump.localmedia.domain

import com.flammky.android.kotlin.coroutine.AndroidCoroutineDispatchers
import com.flammky.android.medialib.common.mediaitem.AudioMetadata
import com.flammky.android.medialib.providers.mediastore.MediaStoreProvider
import com.flammky.android.medialib.providers.mediastore.MediaStoreProvider.ContentObserver.Flag.Companion.isUpdate
import com.flammky.common.kotlin.coroutines.safeCollect
import com.flammky.musicplayer.library.dump.localmedia.data.LocalSongModel
import com.flammky.musicplayer.library.dump.localmedia.data.LocalSongRepository
import dev.dexsr.klio.library.BuildConfig
import dev.dexsr.klio.media.playlist.Playlist
import dev.dexsr.klio.media.playlist.PlaylistItem
import dev.dexsr.klio.media.playlist.RealPlaylistRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

// TODO
interface ObserveLocalSongs {
	fun get(scope: CoroutineScope): ObservableLocalSongs
}

interface ObservableLocalSongs {
	fun collectLocalSongs(): Flow<ImmutableList<LocalSongModel>>
	fun collectRefresh(): Flow<Boolean>
	suspend fun refresh(): Job

	suspend fun refreshAsync(): Deferred<ImmutableList<LocalSongModel>>

	suspend fun observeMetadata(id: String): Flow<AudioMetadata?>
	suspend fun observeArtwork(id: String): Flow<Any?>

	suspend fun refreshMetadata(model: LocalSongModel): Job


	fun release()
}

class RealObservableLocalSongs(
	private val repository: LocalSongRepository,
	// we should have interface on repository instead
	private val mediaStore: MediaStoreProvider,
	private val dispatchers: AndroidCoroutineDispatchers
) : ObservableLocalSongs {

	private val playlistRepo = RealPlaylistRepository()

	private val scheduleMutex = Mutex()
	private var _refreshJob: Job? = null

	private val ioScope = CoroutineScope(dispatchers.io + SupervisorJob())
	private val rememberMutex = Mutex()
	private var _rememberList: ImmutableList<LocalSongModel> = persistentListOf()
	private val scheduledRefresh = mutableListOf<Any?>()

	private val refreshStateMSF = MutableStateFlow(false)
	private val localSongsMSF = MutableStateFlow<ImmutableList<LocalSongModel>>(persistentListOf())

	// remove this and use refreshJob active state instead
	private var _rememberRefreshing = false

	val refreshJob: Job?
		get() = _refreshJob

	private suspend fun sendUpdate(
		list: ImmutableList<LocalSongModel>,
		refreshing: Boolean
	) {
		rememberMutex.withLock {
			_rememberList = list
			_rememberRefreshing = refreshing
		}
		withContext(dispatchers.main) {
			localSongsMSF.value = list
			refreshStateMSF.value = refreshing
		}
		Timber.d("ObserveLocalSongs sendUpdate, refresh: $refreshing, ${refreshStateMSF.value} contents: $list")
	}

	private suspend fun getScheduledSize() = scheduleMutex.withLock { scheduledRefresh.size }
	private suspend fun getRememberedList() = rememberMutex.withLock { _rememberList }

	private suspend fun doScheduledRefresh(id: String? = null): ImmutableList<LocalSongModel> {
		if (id == null) {
			while (true) {
				val size = getScheduledSize()
				val get = repository.getModelsAsync().await().toPersistentList()
				val remains = scheduleMutex.withLock {
					scheduledRefresh.drop(size).let { leftover ->
						scheduledRefresh.clear()
						scheduledRefresh.addAll(leftover)
						scheduledRefresh
					}.also { Timber.d("doScheduledRefresh, refreshed $size at once") }
				}
				if (remains.isEmpty()) return get
			}
		} else {
			return doScheduledRefresh(null)
			// TODO
		}
	}

	private suspend fun scheduleRefresh(id: String? = null, rescan: Boolean = false): Job {
		return scheduleMutex.withLock {
			scheduledRefresh.add(id)
			val remembered = rememberMutex.withLock {
				if (_rememberRefreshing) {
					return _refreshJob!!
				}
				_rememberRefreshing = true
				_rememberList
			}
			_refreshJob = ioScope.launch {
				sendUpdate(remembered, true)
				sendUpdate(doScheduledRefresh(id).also { songs ->
					playlistRepo.updateOrCreate(
						Playlist(
							// fixme: magic literal
							id = "device_songlist",
							snapshotId = "",
							contents = songs.map { PlaylistItem(id = it.id, contentId = it.id) },
							displayName = "Local Files",
							ownerId = "klio:android"
						)
					).await()
						.onSuccess {
							if (BuildConfig.DEBUG) {
								Timber.d("ObservableLocalSongs, updateDeviceSongLists success(id=${it.id}, snapshotId=${it.snapshotId}, contents=${it.contents.map { "(id=${it.id}, contentId=${it.contentId})" }})")
							}
						}
						.onFailure { ex ->
							if (BuildConfig.DEBUG) {
								Timber.d("ObservableLocalSongs, updateDeviceSongLists fail=$ex")
								ex.printStackTrace()
							}
						}
				}, false)
			}
			_refreshJob!!
		}
	}

	private val observer = MediaStoreProvider.ContentObserver { id, uri, flag ->
		Timber.d("ObserveCurrentAvailable $id, $uri, $flag")
		ioScope.launch {
			if (flag.isUpdate) {
				repository.refreshMetadata(id, uri)
				repository.refreshArtwork(id, uri)
			}
			scheduleRefresh(null)
		}
	}

	override fun collectLocalSongs(): Flow<ImmutableList<LocalSongModel>> = localSongsMSF.asStateFlow()
	override fun collectRefresh(): Flow<Boolean> = refreshStateMSF

	override suspend fun refresh(): Job {
		if (scheduleMutex.withLock { _refreshJob?.isActive } != true) {
			scheduleRefresh()
		}
		return _refreshJob!!
	}

	override suspend fun refreshAsync(): Deferred<ImmutableList<LocalSongModel>> {
		return ioScope.async {
			refresh().join()
			rememberMutex.withLock { _rememberList }
		}
	}

	override suspend fun refreshMetadata(model: LocalSongModel): Job = ioScope.launch(Dispatchers.Main) {
		val m = repository.refreshMetadata(model)
		val a = repository.refreshArtwork(model)
		m.join()
		a.join()
	}

	init {
		ioScope.launch {
			val observed = AtomicBoolean(false)
			localSongsMSF.subscriptionCount.safeCollect {
				if (it > 0 && observed.compareAndSet(false, true)) {
					mediaStore.audio.observe(observer)
				} else if (it == 0 && observed.compareAndSet(true, false)) {
					mediaStore.audio.removeObserver(observer)
				}
			}
		}
	}

	override suspend fun observeMetadata(id: String): Flow<AudioMetadata?> = repository.collectMetadata(id)
	override suspend fun observeArtwork(id: String): Flow<Any?> = repository.collectArtwork(id)

	override fun release() {
		ioScope.cancel()
		mediaStore.audio.removeObserver(observer)
		playlistRepo.dispose()
	}
}
