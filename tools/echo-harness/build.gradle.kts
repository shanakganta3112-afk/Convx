// Proves an Echo extension APK loads and answers, outside Android and outside Echo.
// The extension's dex is converted to a jar, loaded with URLClassLoader against Echo's
// published contract, and driven through the same client interfaces Convx uses.
plugins {
    kotlin("jvm") version "2.3.10"
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("dev.brahmkshatriya.echo:common:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application { mainClass.set("harness.MainKt") }

// dex2jar output does not always pass the JVM's bytecode verifier (VerifyError: Bad
// type on operand stack) even though ART accepts the same dex. Skipping verification
// lets the converted classes run; it is a harness-only concession and says nothing
// about how the extension behaves on a device.
tasks.named<JavaExec>("run") {
    jvmArgs("-Xverify:none")
}
