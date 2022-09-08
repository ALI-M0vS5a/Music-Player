package com.kylentt.musicplayer.core.app.device

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable
import com.kylentt.musicplayer.common.lazy.LazyConstructor
import com.kylentt.musicplayer.core.app.permission.AndroidPermissionInfo

class DeviceManager private constructor(private val context: Context) {
	private val permissionInfo = AndroidPermissionInfo(context)
	private val wallpaperManager = WallpaperManager.getInstance(context)

	val wallpaperDrawable: Drawable?
		get() {
			return if (permissionInfo.readExternalStorageAllowed) {
				//noinspection MissingPermission
				wallpaperManager.drawable
			} else {
				null
			}
		}


	companion object {
		private val instance = LazyConstructor<DeviceManager>()
		fun get(application: Application) = instance.construct { DeviceManager(application) }
	}
}