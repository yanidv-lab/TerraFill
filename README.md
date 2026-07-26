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

## Releasing to Google Play

Play does not accept `.apk` uploads — a store submission must be a signed
**Android App Bundle** (`.aab`). The `Release Bundle` workflow builds one.

**One-time setup.** Create the upload keystore on your own machine and keep a
backup somewhere off it: if the keystore is lost, this listing can never be
updated again.

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias upload
```

Base64-encode it and add three repository secrets under
**Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the `.jks` file, base64-encoded |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password (usually the same) |

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Set-Clipboard
```
```bash
# macOS / Linux
base64 -w0 my-upload-key.jks
```

The `.jks` is git-ignored and must never be committed — anyone holding it plus
the password could publish updates as you.

**Building.** Run *Actions → Release Bundle → Run workflow*, or push a `v*` tag.
The workflow runs the full test suite, builds `bundleRelease`, **verifies the
bundle is actually signed**, and attaches it as `TerraFill-release-aab` along
with the R8 mapping file (which is what makes Play Vitals stack traces readable
— keep it for every shipped build).

`versionCode` comes from the workflow run number, so it increases on its own;
Play rejects a versionCode it has already seen. Pass `versionName` when starting
the run to set the version players see.

## Privacy policy

Google Play requires every listing to link a publicly reachable HTTPS privacy
policy. This repo serves one from `docs/` via GitHub Pages:

<https://yanidv-lab.github.io/TerraFill/privacy.html>

To publish it, enable Pages once in **Settings → Pages → Build and deployment →
Deploy from a branch → `main` / `/docs`**. Edits to `docs/privacy.html` then go
live automatically on push.

The policy states that the app collects nothing, which is true today: the
manifest declares no permissions, so the app has no network access, no ads and no
analytics. **Adding an ads SDK would make it inaccurate** — update the policy and
the Play Console Data Safety answers in the same change.

## License

**All rights reserved.** This repository is public for the author's development
workflow only — see [LICENSE](LICENSE). No permission is granted to copy,
modify, republish, or monetize this game or its assets.
