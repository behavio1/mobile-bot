# Architecture

Mobile Bot has two programs that run on the same phone: the Android app and a host in Termux. The app is the interface and the phone's hands; the host keeps the state and starts the AI runs.

## Components

### Android app (`app/`)

- **UI** — Kotlin and Jetpack Compose. `ui/MobileBotApp.kt` holds the main screens (home, chat, agent workspace, powers, automations, profile); onboarding is in `ui/OnboardingTrailScreen.kt`.
- **Setup** — `setup/` checks Termux, asks for the `RUN_COMMAND` permission, sends the bootstrap script to Termux through `RunCommandService`, starts the host and reads `GET /health`. `TermuxContract.kt` pins the versions of Termux, Codex and the host that the app expects.
- **Workspace client** — `workspace/WorkspaceClient.kt` calls the host API on `127.0.0.1:8767`. `HostApiAuth` adds the device token and the language header to every request.
- **Accessibility bridge** — `automation/PhoneAutomationService.kt` is an Android accessibility service with a small HTTP server on `127.0.0.1:8768`. It reads the active window, lists launchable apps, opens apps and URLs, taps, types, scrolls and goes back or home. It accepts only requests with the device token.
- **Background work** — `AutomationBackgroundCoordinator` uses WorkManager to ask the host to run due automations, and `AutomationNotificationManager` tells you when an automation waits for the phone to be unlocked.

### Termux host (`host/`)

TypeScript compiled to ES modules and copied into `app/src/main/assets`. The bootstrap script installs them in `~/.mobile-bot-ui/` and starts `host.mjs` with Node.js.

- **HTTP API** on `127.0.0.1:8767`: agents, conversations, runs, powers, the power workshop, automations, themes, runtime selection and health.
- **State** in SQLite (`~/.mobile-bot-ui/state.sqlite`): agents, conversations, messages, runs and automations.
- **Agent folders** in `~/.mobile-bot-ui/workspaces/<agent-id>/`. Before each run the host writes `AGENTS.md` (role, owner profile, powers, phone tool rules) and copies the assigned powers into `.agents/skills/`. Agents keep their own notes in `okf/`, `work/` and `results/`.
- **Runs** start `codex exec` (or `codex exec resume` for an existing conversation) in the agent's folder and stream its JSON events. When the agent used the phone, the host asks the app to come back to the foreground with the answer.
- **Scheduler** checks every 30 seconds for automations that are due. An automation that needs the phone while it is locked waits and is retried later.
- **Power workshop** (`skillWorkshop.ts`) turns a description into a new power in three Codex steps: research, build and validation. Only a power that passes its own checks is added to the library.
- **Local AI** (`localAi.ts`, `piRuntime.ts`, experimental) downloads llama.cpp and Qwen3.5 0.8B and runs conversations through the Pi coding agent with a reduced phone toolset.

### Helpers installed in Termux

- `phone-client.mjs` — the command-line phone tool the agents call (`status`, `observe`, `list-apps`, `open-app`, `click`, `set-text`, `scroll`, `back`, `home`, …). It talks to the accessibility bridge and reports each action to the host.
- `automation-client.mjs` — lets an agent register a 15-minute automation for the current conversation.

## A message, step by step

1. You send a message in the chat. The app calls `POST /runs` with the agent ID and your text.
2. The host stores the message, writes the agent's `AGENTS.md` and starts Codex in the agent's folder.
3. If the task needs the phone, Codex runs `phone-client.mjs`, which calls the bridge in the app. The app performs the action and returns what is on screen.
4. Codex finishes with a reply. The host saves it, and if the phone was used, asks the app to come back to the foreground.
5. The app shows the reply as Markdown.

## Languages

The app has English (`values/strings.xml`) and Polish (`values-pl/strings.xml`) resources. It sends `Accept-Language: en` or `pl` to the host and passes `MOBILE_BOT_LOCALE` when it starts the host. The host serves untouched built-in agent texts, conversation starters and workshop messages in that language (`host/src/locale.ts`) and regenerates conversation starters when the language changes. Agent instructions are in English and tell the agent to reply in the language you write in.

## Versions and upgrades

`HOST_VERSION`, `PROTOCOL_VERSION` and `ENVIRONMENT_REVISION` are defined in `host/src/index.ts` and mirrored in `TermuxContract.kt`. When the installed host reports different values, the app shows the setup step again and reinstalls only what changed. Agent data, the Codex sign-in and the downloaded model are kept.
