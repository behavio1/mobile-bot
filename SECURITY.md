# Security policy

## Reporting a vulnerability

Please report security problems privately through GitHub: open the **Security** tab of this repository and choose **Report a vulnerability**. Do not open a public issue for anything that could let another app, a web page or a remote party control the phone or read agent data.

Include the Mobile Bot version (shown in Android app info), the host version (`GET http://127.0.0.1:8767/health` from Termux), the Android version and the steps to reproduce.

## What Mobile Bot protects

- **Local API.** The Termux host (`127.0.0.1:8767`) and the accessibility bridge in the app (`127.0.0.1:8768`) listen only on the loopback interface. Every host route except `GET /health` and every bridge action require `Authorization: Bearer <device token>`. The token is 32 random bytes created by the app and stored in its private preferences and in `~/.mobile-bot-ui/device-token` (mode 600) in Termux.
- **Credentials.** Codex sign-in happens in Termux with OpenAI's device-code flow. Mobile Bot never reads or stores the password or the Codex auth file.
- **Agent files.** The in-app file viewer is read-only and limited to Markdown files inside the selected agent's folder.

## What it does not protect

- Codex runs with `--dangerously-bypass-approvals-and-sandbox`. Inside Termux an agent has the same rights as you have in a Termux shell, including network access.
- Anything running inside Termux, including other scripts you install there, can read the device token and call the host.
- With phone control turned on, an agent can act in any app that is on screen. Rules such as "ask before sending a message" are instructions to the model.
- Text an agent reads from websites, emails or other apps can contain prompt injection. Agents are told to treat it as data, which reduces but does not remove the risk.

Use a phone or work profile without sensitive accounts if you want to experiment freely, and turn the accessibility service off when you are not using phone control.
