// The extension contract: types, models and capability interfaces only.
// Deliberately a plain Kotlin/JVM library — no Android, no network — so both the
// host app and any extension implementation can depend on it, and so it stays
// unit-testable off-device.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
