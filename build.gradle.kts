plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// The coordinates applications depend on, com.reco1l.verktex:<module>, which a build including
// this one substitutes with the projects.
subprojects {
    group = "com.reco1l.verktex"
}

// The UI test browser by the same path, :verktex:run, in both builds: here it's the task of the
// :verktex module, and from a build including this one :verktex is this root project. The run
// configuration in .run relies on it.
tasks.register("run") {
    group = "application"
    description = "Runs the UI test browser of Verktex on desktop."
    dependsOn(":verktex:run")
}
