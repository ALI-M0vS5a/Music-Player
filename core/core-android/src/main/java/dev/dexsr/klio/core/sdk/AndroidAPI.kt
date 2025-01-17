package com.flammky.musicplayer.core.sdk

import android.os.Build
import dev.dexsr.klio.core.BuildConfig
import dev.dexsr.klio.core.sdk.UpsideDownCake

// maybe: Object receiver instead

/**
 * @see AndroidBuildVersion
 */
abstract class AndroidAPI {

    abstract val buildcode: BuildCode

    companion object : AndroidAPI() {

        private val current: AndroidAPI = when(val sdk = Build.VERSION.SDK_INT) {
            Tiramisu.buildcode.CODE_INT -> Tiramisu
            SnowConeV2.buildcode.CODE_INT -> SnowConeV2
            SnowCone.buildcode.CODE_INT -> SnowCone
            AndroidR.buildcode.CODE_INT -> AndroidR
            Q.buildcode.CODE_INT -> Q
            Pie.buildcode.CODE_INT -> Pie
            Oreo.buildcode.CODE_INT -> Oreo
            Nougat.buildcode.CODE_INT -> Nougat
            else -> if (BuildConfig.DEBUG) {
                // we don't target these API yet, but build tools run on it
                when(sdk) {
                    UpsideDownCake.buildcode.CODE_INT -> UpsideDownCake
                    else -> error("Unsupported Android API version: $sdk")
                }
            } else {
                error("Unsupported Android API version: $sdk")
            }
        }

        override val buildcode: BuildCode = current.buildcode
    }
}