# API 36 build contract

- `compileSdk = 36` and `targetSdk = 36`.
- Generated apps use minSdk 30 (Android 11+) and must guard newer platform APIs. The debug application ID must be explicit and must not collide with Mobile Bot.
- Keep generated signing material out of the project. A normal debug keystore from the build environment is sufficient for emulator testing.
- Standing authorization covers minimal public Termux build packages, the build itself, and installation/start of the current generated debug APK on the target Android phone. No repeated approval is required for those steps.
- A passing build alone is insufficient for an E2E result. Install the APK, launch its exported activity, confirm the generated package is resumed, and read visible UI from the emulator.
- For a clock, verify a time matching `HH:mm:ss` is visible and changes after at least one second.

## On-phone build and delivery

The bundled native template can build in Termux with JDK 21, Gradle, SDK Platform 36 / Build-Tools 36 and Termux's native `aapt2`. Reuse the installed SDK when present; do not assume Python, ripgrep or an authorized ADB connection exist. For the included template, from its project directory:

```sh
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
gradle --no-daemon --max-workers=2 -Dorg.gradle.vfs.watch=false \
  -Pandroid.aapt2FromMavenOverride="$(command -v aapt2)" assembleDebug
```

Verify the APK's package/launcher/SDK with `aapt2 dump badging`. Keep the build log and exact APK path. A rerun should reuse the same project and preserve existing app data.

For local delivery, first bring Termux to the foreground with the phone helper. Pass the verified absolute APK URI to its sharing receiver:

```sh
termux-open --view --content-type application/vnd.android.package-archive "file://$apk_path"
```

Observe the screen; if Android shows a chooser, select Package installer, then Just once. A successful `termux-open` exit does not prove installation. Confirm installation through `list-apps`, open the exact returned package and inspect its UI. Never install through a desktop runner and report it as agent delivery. Do not enable USB/wireless debugging merely to avoid a protected installation confirmation.

Android 16 can hide sensitive controls from Accessibility, including an installation confirmation. If the installer is readable but Install is absent from the helper's fresh observation, distinguish this from disconnected phone control. Ask the owner to confirm the visible installation, then resume with `status`, `list-apps` and `observe`. Do not try coordinate clicks, privileged shells, or false accessibility-tool declarations to bypass a protected control. Report the required user confirmation separately from successful build/install/run.

Reference: [Android 16 sensitive Accessibility views](https://developer.android.com/blog/posts/building-a-safer-android-and-google-play-together).

## Behavioral evidence

Use synthetic test data. For a task manager, verify create/edit/complete/reopen/delete (including cancel), blank-title validation, both available themes, and data after reopening. A real process restart is a separate check from navigating away. If the phone executor cannot restart a process, report that limitation rather than claiming cold-start persistence. Clean up only your own test items. Preserve reports after each meaningful stage so an interrupted run can continue without rebuilding or reinstalling blindly.
