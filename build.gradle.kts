// Top-level build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // R7: Compose compiler plugin (built-in to Kotlin 2.0+) — needed because
    // Settings sliders use ComposeView for M3 Expressive thick-track + tall-handle
    // Slider, which the View-system Material Components library doesn't provide.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
