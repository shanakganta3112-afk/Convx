// Minimal Android app whose only job is to load the Echo extension APK with
// DexClassLoader on a real ART runtime and print what it returns. This is the same
// mechanism Convx uses; it proves the mechanism, not Convx's screens.
plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.3.10"
}

android {
    namespace = "probe.echo"
    compileSdk = 35

    defaultConfig {
        applicationId = "probe.echo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("dev.brahmkshatriya.echo:common:1.0.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
