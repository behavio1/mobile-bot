# Mobile Bot user guide

This guide follows the video tutorial: setup, your first conversation, phone control, automations, powers, custom agents and themes. Screens are from Mobile Bot 0.1 on Android 15 and 16.

- [1. Before you start](#1-before-you-start)
- [2. Set up the app](#2-set-up-the-app)
- [3. Chat with an agent](#3-chat-with-an-agent)
- [4. Let agents use your phone](#4-let-agents-use-your-phone)
- [5. Automations](#5-automations)
- [6. Powers](#6-powers)
- [7. Create your own agent](#7-create-your-own-agent)
- [8. Themes, profile and agent files](#8-themes-profile-and-agent-files)
- [9. On-phone AI (experimental)](#9-on-phone-ai-experimental)
- [10. Troubleshooting](#10-troubleshooting)

## 1. Before you start

You need:

- an Android phone with Android 11 or newer;
- [Termux](https://github.com/termux/termux-app/releases) from GitHub or F-Droid (not the old Google Play build);
- a ChatGPT account that can use Codex;
- an internet connection for setup and for Codex.

Read the [security notes](../SECURITY.md) first. Agents run Codex without approval prompts inside Termux, and phone control lets them act in any app you can see.

## 2. Set up the app

Open Mobile Bot. The **First mission** screen shows five steps: Base, Crew, Powers, Phone and Mission. Only Base is required.

<img src="images/setup-termux.png" alt="Termux is required step" width="270" align="right">

**Install Termux.** Tap **Set up the app**. If Termux is missing, tap **Get Termux**: Mobile Bot opens the official Termux release and Android asks you to confirm the installation. Open Termux once and wait until it shows the `$` prompt.

**Allow access.** Back in Mobile Bot, tap **Allow Termux access** and choose **Allow**. Android then lets Mobile Bot send commands to Termux.

<br clear="right">

<img src="images/setup-command.png" alt="One more step in Termux with the prepared command" width="270" align="right">

**Confirm once in Termux.** Termux only accepts commands from other apps after you allow it in its settings file. Tap **Copy the step and open Termux**, long-press the empty Termux screen, choose **Paste** and press **Enter**. Come back to Mobile Bot and tap **Done — check access**.

The command it copies is:

```bash
mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings
```

<br clear="right">

<img src="images/setup-install.png" alt="Setting up the app" width="270" align="right">

**Install the host.** Choose how the AI should run (**Codex** is the default) and tap **Start**. Mobile Bot installs Node.js, Codex CLI and its local host in Termux. Keep the screen open; the first run takes a few minutes, later updates are faster.

<br clear="right">

<img src="images/setup-signin.png" alt="Connect your Codex account" width="270" align="right">

**Sign in to Codex.** Tap **Sign in to Codex**. Termux shows a link (`https://auth.openai.com/codex/device`) and a one-time code. Open the link on any device, sign in with your ChatGPT account and enter the code. Return to Mobile Bot; the Base step is now done. Mobile Bot never sees your password.

<br clear="right">

## 3. Chat with an agent

<img src="images/home.png" alt="Home screen with agent cards" width="270" align="right">

The home screen shows your agents. Each card shows how many automations and conversations the agent has. Tap a card to open a new conversation; use the **⋮** menu on a card to open its settings, hide it or delete it.

The built-in agents are:

| Agent | Good at |
| --- | --- |
| Atlas | Research, comparisons and monitoring |
| Nova | Organizing and phone tasks |
| Echo | Writing, summaries and communication |
| Developer | Building and testing Android apps on the phone |
| Mail | Reading and answering email in your mail app |
| Shopping | Allegro and lowest-price monitoring |

<br clear="right">

<img src="images/chat.png" alt="Nova answering with a morning plan" width="270" align="right">

Type a message and tap the send button. A new conversation shows four suggestions that fit the agent's role; tap one to use it. The agent's reply appears as formatted text with lists, tables and links. Pinch with two fingers to change the text size.

Each agent keeps every conversation. Tap the conversations icon at the top left of the chat to switch between them or start a new one.

<br clear="right">

## 4. Let agents use your phone

<img src="images/accessibility.png" alt="Agent phone control in Android accessibility settings" width="270" align="right">

To let agents open apps and read the screen, turn on the accessibility service:

1. On the First mission screen, open **Phone** and tap **Show the steps**, or go to Android **Settings → Accessibility**.
2. Open **Agent phone control** (under *Downloaded apps*) and turn on **Use Agent phone control**.
3. Return to Mobile Bot and tap **Check access**.

Open the apps you want agents to use at least once, sign in and grant their permissions yourself. Agents cannot pass the lock screen and never enter passwords, PINs or codes.

<br clear="right">

<img src="images/phone-clock.png" alt="Clock app opened by Nova" width="200" align="right">
<img src="images/phone-control.png" alt="Nova reporting the alarms" width="200" align="right">

Now ask, for example: *Open the Clock app on this phone and tell me which alarms are set.* The agent switches to Clock, reads the screen, comes back to Mobile Bot and tells you what it found. If it cannot check something, such as the on/off state of a switch, it says so.

Agents ask before sending messages, posting, buying or deleting. Turn the service off when you do not need it.

<br clear="right">

## 5. Automations

<img src="images/automation.png" alt="Atlas setting up a rain alert" width="270" align="right">

Ask an agent to repeat something, for example: *Every 15 minutes, check the forecast for Warsaw and tell me if rain is expected in the next hour.* The agent creates the automation for the current conversation and usually runs the first check right away.

Automations run every 15 minutes. If a check needs the phone while it is locked, it waits, shows a notification and runs after you unlock the phone.

<br clear="right">

<img src="images/automations.png" alt="Automations screen with details" width="270" align="right">

Open **Automations** on the home screen to see all of them. The switch pauses or resumes an automation; a paused automation finishes the check it is running and then stops. **Details** shows the last result, a link to the conversation and the run history with delays and skipped runs.

<br clear="right">

## 6. Powers

<img src="images/powers.png" alt="Nova's powers" width="270" align="right">

Powers are Codex skills: instructions and helper scripts an agent reads before a matching task. Open an agent, tap the powers icon at the top right of the chat, tick the powers you want and tap **Save assignments**. Every agent also inherits the *agent-workspace-okf* power that organizes its notes.

<br clear="right">

<img src="images/workshop.png" alt="Workshop building a new power" width="200" align="right">
<img src="images/power-ready.png" alt="Task Checklist Power ready to use" width="200" align="right">

To create a power, tap **+ New power**, describe what it should do and tap **Build the power**. The workshop researches the task, builds the power and tests it, which can take several minutes; you can go back to your conversation meanwhile. If it needs more information it asks one question. A power that passes its checks appears in the library, where you can read its instructions and assign it to agents.

<br clear="right">

## 7. Create your own agent

<img src="images/new-agent.png" alt="New agent form" width="200" align="right">
<img src="images/new-agent-chat.png" alt="New Chef agent with conversation suggestions" width="200" align="right">

Tap **+ New agent** on the home screen. Give the agent a name, choose one of 15 avatars and, optionally, describe its role and a first task. Tap **Create agent** (or **Create and send** with a first task). The agent gets its own conversation, its own working folder and suggestions that fit its role.

Change the name, photo, role or powers later from the agent's workspace panel.

<br clear="right">

## 8. Themes, profile and agent files

<img src="images/themes.png" alt="Animals theme" width="270" align="right">

Tap **Profile** on the home screen:

- **App style** changes colors, illustrations and avatars: Superheroes, Office, Animals or Influencers. Agents with the *theme-builder* power can also create custom color themes.
- **About you** is a short description every agent receives with each task, for example your name, your job and what you care about.

<br clear="right">

<img src="images/files.png" alt="Reading AGENTS.md in the file viewer" width="270" align="right">

Each agent works in its own folder. Open the conversations panel and tap **Agent files** to read the Markdown notes, results and the `AGENTS.md` instructions the agent received. The viewer is read-only.

<br clear="right">

## 9. On-phone AI (experimental)

During setup you can choose **AI on the phone** instead of Codex. Mobile Bot downloads llama.cpp and the Qwen3.5 0.8B model (533 MB) and runs conversations without sending them to the cloud. The model is small: it handles short questions, but often fails multi-step phone tasks. The choice is offered during setup; the app has no switch for it afterwards yet.

## 10. Troubleshooting

| Problem | What to do |
| --- | --- |
| Setup keeps asking for the Termux step | Make sure you pasted the command in Termux and pressed Enter, then tap **Done — check access**. |
| "Termux requires Display over other apps" toast | Allow it for Termux in Android **Settings → Apps → Termux** if sign-in or the host does not start from the background. |
| Phone control turned off after an update | Android turns accessibility services off when an app is reinstalled. Turn **Agent phone control** on again. |
| An automation shows *Waiting for unlock* | Unlock the phone; the check runs shortly after. |
| Replies stop after a long idle time | Open Mobile Bot; it restarts the host in Termux if Android stopped it. |
| You want to start over | Clear Mobile Bot's storage in Android settings and delete `~/.mobile-bot-ui` in Termux. This removes agents, conversations and powers. |
