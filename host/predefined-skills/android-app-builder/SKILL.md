---
name: android-app-builder
description: Create, build, install, test and update native Android applications on the owner's Android 11+ phone. Keep each application's source, identity, build results and progress in a persistent project folder.
---

# Android App Builder

Work on the actual Android phone, not an assumed emulator or desktop. Use the owner's requested features as acceptance criteria. Ask only about decisions that materially change the app, such as accounts, external services or ambiguous existing projects. Do not invent a backend for a local utility.

## Persistent project

Run the bundled helper from the agent workspace. Start by listing `projects/*/project.json` and checking whether the requested app already exists. For a new app choose a stable readable slug and unique package ID, then initialize exactly once:

```sh
node .agents/skills/android-app-builder/system/scripts/project.mjs init task-manager pl.owner.taskmanager "Moje zadania"
```

The names above are examples, not a fixed package or role. Repeating init with the same identity preserves files. A conflicting identity fails instead of overwriting. Each project has `app-project/`, `project.json`, `README.md`, `reports/` and `artifacts/`. Record the agreed scope in README.md. Replace the starter UI with the requested functionality; an unchanged starter is not a finished app.

For an existing project outside this layout, inspect and preserve it. Do not create a second app or move/delete working source merely to match the convention. For follow-up changes retain package ID and signing key, increase versionCode for an update, and migrate stored data without clearing it. Never uninstall the user's app as an update strategy. Keep signing keys outside source and reports; if a key is unavailable, explain the update limitation instead of silently replacing identity.

## Check the phone and build

Default generated apps support Android 11+ (`minSdk = 30`); compile against Android API 36 and target API 36 with the tested template. These values are different: the phone does not need API36 to run the result. Guard APIs newer than 30 and run lint; do not raise minSdk to hide errors without a user requirement. Inspect actual API, ABI, screen and resources. Android version alone does not prove that every native library/tool will run on this processor.

```sh
node .agents/skills/android-app-builder/system/scripts/project.mjs check task-manager
```

If tools are missing, use the bundled Termux installer, which reuses installed components and verifies pinned Google SDK archives. Log its output to the project's reports folder. The owner's app-building request covers the minimal public build dependencies and local installation; it does not authorize purchases, Play Store publication or unrelated changes.

```sh
node .agents/skills/android-app-builder/system/scripts/setup-toolchain.mjs
node .agents/skills/android-app-builder/system/scripts/project.mjs check task-manager
node .agents/skills/android-app-builder/system/scripts/project.mjs build task-manager
node .agents/skills/android-app-builder/system/scripts/project.mjs status task-manager
```

Build starts a detached worker so a long compilation can continue past an agent turn. It saves a log, runs assembleDebug and lintDebug, verifies APK identity/launcher and records its hash. Read status and the log rather than starting duplicate builds. Android can still kill Termux; after interruption inspect processes and state before resuming. `apk_ready` proves only build success, not installation or behavior. The helper's tool check is not a guarantee of sufficient resources for every project.

## Install, verify, resume

Use the local phone helper and read [the build and delivery contract](references/api36-build-contract.md). Observe the actual installer, wait for its result, discover the installed package and launch it. Do not require desktop ADB or enable debugging. If Android requires a protected confirmation, keep the exact pending project and APK and resume after that confirmation; do not rebuild or install blindly.

Test the requested behaviors through the actual UI, including invalid input and persistence where relevant. Use synthetic data, clean up only your test items, and inspect narrow crash logs on failure. Check layouts in both themes and on the actual screen. Record each test and evidence in `reports/`; distinguish opening an Activity from restarting its process. For updates verify existing user data survives. Preserve source, current APK, build log and a concise README result so the next turn resumes the same project.

The final response describes what the user can open and what was verified. Never report a completed app from source code, a successful build, or an unconfirmed installer alone.
