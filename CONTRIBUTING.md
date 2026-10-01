# Contributing

Thanks for helping with Mobile Bot. Bug reports, fixes and focused improvements are welcome.

## Before you start

- For anything larger than a small fix, open an issue first and describe the problem you want to solve.
- Security problems go through private reporting; see [SECURITY.md](SECURITY.md).

## Working on the code

- The Android app is in `app/` (Kotlin, Jetpack Compose, minSdk 30). The Termux host is in `host/` (TypeScript, Node.js 24).
- `npm --prefix host test` builds the host, copies it into `app/src/main/assets` and runs the host tests. Commit the updated assets together with the host source.
- When you change the host, raise `HOST_VERSION` and `ENVIRONMENT_REVISION` in both `host/src/index.ts` and `app/src/main/java/one/behavio/mobilebotui/setup/TermuxContract.kt`. Phones reinstall the host only when these change.
- Every visible text goes into `app/src/main/res/values/strings.xml` (English) and `values-pl/strings.xml` (Polish). Host texts that users see live in `host/src/locale.ts`.
- Keep labels short and say what happens when a control is used.
- Do not commit agent data, logs, screenshots with personal data, `local.properties`, keystores or credentials.

## Checks

Run these before opening a pull request:

```bash
npm --prefix host test
./gradlew testDebugUnitTest lintDebug assembleDebug
```

If you changed screens, also run `./gradlew connectedDebugAndroidTest` on an emulator and check the change in a narrow portrait window and with large text.

## Pull requests

- One topic per pull request, with a short description of what changed, how you checked it and any known limits.
- Add or update tests for behavior changes.
- By contributing you agree that your work is released under the [MIT License](LICENSE).
