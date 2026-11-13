# S3 Monitor

Android app, Material 3 Expressive light theme, purple baseline.
View system + XML + ViewBinding. **No Jetpack Compose.**

Package: `com.trmi.s3monitor`.

## Stack

| Layer | Choice |
|---|---|
| UI | View system + XML layouts |
| View access | ViewBinding (`buildFeatures.viewBinding = true`) |
| M3 components | `com.google.android.material:material:1.12.0` |
| Min SDK | 26 |
| Target / compile SDK | 34 |
| Language | Kotlin (JVM 17) |

## Status

| Round | Screen | Status |
|---|---|---|
| 1 | Home | ✅ shipped |
| 2 | Controller | ✅ shipped |
| 3 | History | ✅ shipped |
| 4 | Settings | ✅ shipped (R7 update: filled card "Normal settings" + 6 horizontal ComposeView sliders) |
| 5 | Story | ✅ shipped |
| 6 | Resources | ✅ shipped |
| 7 | Drive | ✅ shipped (new in R7 spec update) |

**All 7 screens shipped.** The nav router is fully wired — every
destination on both bars routes to a real Activity.

Each round ships the screen's `activity_*.xml` + `*Activity.kt` plus any
menu / drawable / string resources unique to it. Shared infrastructure
(theme, colors, nav bars, animations) lives in round 1.

## Architecture

```
app/src/main/
├── AndroidManifest.xml         # Activities registered here as they ship
├── java/com/trmi/s3monitor/
│   ├── S3App.kt                # Application — singletons land here
│   ├── ui/
│   │   ├── base/
│   │   │   └── BaseScreenActivity.kt   # Edge-to-edge, slide transitions, nav router
│   │   ├── home/
│   │   │   └── HomeActivity.kt         # ← Round 1
│   │   ├── controller/
│   │   │   └── ControllerActivity.kt   # ← Round 2
│   │   ├── history/
│   │   │   └── HistoryActivity.kt      # ← Round 3
│   │   └── settings/
│   │       ├── SettingsActivity.kt     # ← Round 4
│   │       └── ThresholdDefaults.kt    # ← Round 4 (slider ranges + keys)
│   │   └── story/
│   │       └── StoryActivity.kt        # ← Round 5
│   │   └── resources/
│   │       └── ResourcesActivity.kt    # ← Round 6
│   │   └── drive/
│   │       └── DriveActivity.kt        # ← Round 7 (new screen)
│   └── util/
│       └── NavBars.kt          # Shared nav bar helpers
└── res/
    ├── layout/
    │   ├── activity_home.xml           # ← Round 1
    │   ├── activity_controller.xml     # ← Round 2
    │   ├── activity_history.xml        # ← Round 3
    │   ├── activity_settings.xml       # ← Round 4 (R7 refactored)
    │   ├── activity_story.xml          # ← Round 5
    │   ├── activity_resources.xml      # ← Round 6
    │   ├── activity_drive.xml          # ← Round 7 (new screen)
    │   ├── item_drive_gpio.xml         # ← Round 7 (reusable list item)
    │   ├── layout_nav_bar_main.xml     # Shared: Home | Controller | History | Drive (R7: 4 destinations)
    │   └── layout_nav_bar_secondary.xml# Shared: Settings | Story | Resources
    ├── menu/
    │   ├── menu_nav_main.xml           # R7: now 4 destinations
    │   ├── menu_nav_secondary.xml
    │   ├── menu_topbar_home.xml
    │   ├── menu_topbar_controller.xml  # ← Round 2
    │   ├── menu_topbar_history.xml     # ← Round 3
    │   ├── menu_topbar_settings.xml    # ← Round 4
    │   └── menu_topbar_drive.xml       # ← Round 7
    ├── values/
    │   ├── colors.xml          # M3 purple baseline
    │   ├── themes.xml          # Theme.S3Monitor + card / shape overrides
    │   ├── strings.xml
    │   └── dimens.xml
    ├── anim/
    │   ├── slide_in_right.xml  # Entry transition
    │   ├── slide_out_left.xml  # Paired exit (current screen drifts left)
    │   ├── slide_in_left.xml   # Reverse entry on Back
    │   ├── slide_out_right.xml # Reverse exit on Back
    │   └── hold.xml
    └── drawable/               # Material Symbols Rounded icons (vector) + scrims
```

## How navigation works

`BaseScreenActivity` declares two open methods — `openMainDestination(itemId)`
and `openSecondaryDestination(itemId)` — that route nav-bar taps to the
right `Activity`. After R7, all 4 main-bar destinations (Home,
Controller, History, Drive) and all 3 secondary-bar destinations
(Settings, Story, Resources) are wired.

Slide-in-from-right is applied automatically in `onCreate()` via
`overridePendingTransition()`. System back plays it in reverse via an
`OnBackPressedCallback`.

## Hybrid Compose (R7)

The Settings screen's threshold sliders use `ComposeView` (Jetpack
Compose material3 Slider/RangeSlider) embedded inside the View-system
layout. The View-system Material Components library doesn't ship the
M3 Expressive thick-track + tall-handle styling the R7 spec calls for;
Compose's material3 Slider is the closest stable approximation.

`compose = true` is enabled in `app/build.gradle.kts`, the Compose
compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is applied, and
the compose-bom 2024.10.01 (material3 1.3.0) is added. No screen uses
Compose for anything other than the 6 sliders in Settings — every other
UI surface is View system + XML + ViewBinding.

For the experimental M3 Expressive Slider (16dp track, 4x44dp handle),
material3 1.4+ alpha would be needed — out of scope for this round.

## Building

```
cd S3Monitor
./gradlew :app:assembleRelease
```

You'll need JDK 17 and Android SDK 34. Generate a Gradle wrapper first
with `gradle wrapper --gradle-version 8.9` if you don't already have one.
