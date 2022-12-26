plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("dagger.hilt.android.plugin")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.flammky.musicplayer.base"
    compileSdk = 33

    defaultConfig {
        minSdk = 24
        targetSdk = 33
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api(project(":common:kotlin"))
    api(project(":common:android"))
    api(project(":common:androidx"))

    //
    // Graphics
    //

    /* coil-kt */
    dependencies {
        val v = "2.2.2"
        api("io.coil-kt:coil:$v")
        api("io.coil-kt:coil-gif:$v")
    }

    //
    // Kotlin-common
    //

    /* jetbrains.kotlinx */
    dependencies {
        // Collections.Immutable
        val vCollectionImmutable = "0.3.5"
        api("org.jetbrains.kotlinx:kotlinx-collections-immutable:$vCollectionImmutable")

        // coroutines-guava
        val coroutines_guava = "kotlinx-coroutines-guava:1.6.4"
        api("org.jetbrains.kotlinx:$coroutines_guava")
    }

    //
    // DI
    //
    
    /* google.dagger */
    dependencies {

        // Hilt-Android
        val vHiltAndroid = "2.44"
        api("com.google.dagger:hilt-android:$vHiltAndroid")
        kapt("com.google.dagger:hilt-android-compiler:$vHiltAndroid")
    }

    //
    // Java-common
    //

    /* google.guava */
    dependencies {

        // android
        val vAndroid = "31.1-android"
        api("com.google.guava:guava:$vAndroid")
    }

    //
    // Debug
    //

    @Suppress("SpellCheckingInspection")
    /* jakeWharton.timber */
    dependencies {
        val v = "5.0.1"
        api("com.jakewharton.timber:timber:$v")
    }

    //
    // Persistence
    //

    /* androidx.datastore */
    dependencies {
        val v = "1.1.0-alpha01"
        api("androidx.datastore:datastore:$v")
    }

    /* androidx.room */
    dependencies {
        val v = "2.4.3"
        api("androidx.room:room-runtime:$v")
        api("androidx.room:room-ktx:$v")
        api("androidx.room:room-guava:$v")
        ksp("androidx.room:room-compiler:$v")
    }

    //
    // Secure
    //

    /* androidx.secure */
    dependencies {
        val v = "1.0.0"
        api("androidx.security:security-crypto:$v")
    }
}