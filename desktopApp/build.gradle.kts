import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The desktop application of Verktex: the browser of its own UI tests, over the desktop target of
// :shared. See docs/desktop-platform.md.
//
// ./gradlew :desktopApp:run --args="--test 'Verktex/Text' --screenshot build/text.png"
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
}

application {
    mainClass.set("com.reco1l.verktex.desktop.testing.VerktexUITestsMainKt")

    // GLFW has to run on the first thread on macOS.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
}

tasks.named<JavaExec>("run") {
    // Relative paths given in --args, e.g. --screenshot, resolve against the root of Verktex.
    workingDir = rootProject.projectDir

    // FFmpeg's libraries for videos, which :shared downloads.
    dependsOn(":shared:prepareFFmpeg")
    systemProperty("verktex.ffmpeg.path", project(":shared").layout.buildDirectory.dir("ffmpeg").get().asFile.absolutePath)
}
