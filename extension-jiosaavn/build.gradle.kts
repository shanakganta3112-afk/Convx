// The JioSaavn extension: implements the extension contract against JioSaavn's own
// private api.php endpoints. Plain Kotlin/JVM so the parsers are testable off-device.
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

dependencies {
    api(project(":extension-api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
}

// The live probe prints the API's real response shapes; surface them in CI logs.
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
