# TerraFill

A retro-arcade **Xonix/Qix-style territory-capture game** for Android, built with Kotlin and Jetpack Compose.

Steer your cursor off the safe border to draw a trail through open territory. Reconnect to captured ground to claim the enclosed area — but any region containing an enemy stays wild. Capture the target percentage of the field to clear the level, and don't let enemies touch you or your unfinished trail.

## Project layout

| Path | What it is |
|---|---|
| `engine/` | The game simulation (grid, player, trail, flood fill, enemies, scoring). **Pure Kotlin, no Android dependencies** — built as a standalone Gradle build included into the app via composite build. |
| `app/` | The Android app: Compose UI, screens, navigation, ViewModel, DataStore persistence. |

## Running the app

**Prerequisites:** [Android Studio](https://developer.android.com/studio) (with JDK 17+; the project targets Gradle 9.3.1 / AGP 9.1.1).

1. Open Android Studio, choose **Open**, and select this project directory.
2. Let Gradle sync finish.
3. Run on an emulator or device (**Run ▶**).

## Testing the engine without Android

The engine builds and tests with nothing but a JDK — no Android SDK needed:

```bash
cd engine
../gradlew test        # or plain `gradle test`
```

Engine tests live in `engine/src/test/kotlin/` and cover movement, trail drawing, region capture, flood fill, collisions, lives, and level completion.

## App-side tests

```bash
./gradlew :app:testDebugUnitTest
```

Includes Robolectric/Compose UI tests and a Roborazzi screenshot test.

## License

**All rights reserved.** This repository is public for the author's development
workflow only — see [LICENSE](LICENSE). No permission is granted to copy,
modify, republish, or monetize this game or its assets.
