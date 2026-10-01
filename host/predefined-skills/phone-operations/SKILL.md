---
name: phone-operations
description: Prepare and perform controlled actions on the phone with state readback. Use when an assigned agent is asked to operate an installed app or change device state.
---

# Phone Operations

Use the local phone helper at `$HOME/.mobile-bot-ui/phone-client.mjs`. Start with `status`. Discover launchable applications before choosing a package ID:

```sh
node "$HOME/.mobile-bot-ui/phone-client.mjs" status
node "$HOME/.mobile-bot-ui/phone-client.mjs" list-apps
node "$HOME/.mobile-bot-ui/phone-client.mjs" list-apps "APP NAME"
node "$HOME/.mobile-bot-ui/phone-client.mjs" home
node "$HOME/.mobile-bot-ui/phone-client.mjs" open-app "PACKAGE.ID"
node "$HOME/.mobile-bot-ui/phone-client.mjs" open-sms
node "$HOME/.mobile-bot-ui/phone-client.mjs" open-mail
node "$HOME/.mobile-bot-ui/phone-client.mjs" open-url "https://example.com/path"
```

Phone use applies to ALL installed launchable applications the owner requests, not only the examples in the playbooks. `list-apps` is discovery, never an allowlist. Refresh it for a newly requested app; use `list-apps "APP NAME"` to search by display name or package without a long result being truncated. Do not reuse an earlier list as proof that another app is unavailable.

If a named application is absent, do not immediately hand the task back or claim it is blocked. Use `home`, inspect the returned launcher observation, and use visible launcher controls/search to find and open the requested native app. Verify its foreground package from a fresh observation. Never invent a package ID. Only report an app as unavailable after these discovery attempts actually fail; distinguish missing installation, another profile, and a protected system gate when evidenced. Do not substitute its website for the requested native app.

`list-apps` returns launchable entries as `{name, packageName}`. Use the returned exact `packageName`; do not guess one from an app's display name. `open-sms` resolves the phone's default messaging handler, `open-mail` resolves the default mail category, and `open-url` accepts only complete `http` or `https` URLs. A successful open includes a fresh observation whose `packageName` matches the resolved target. `APP_NOT_FOUND`, `APP_HAS_NO_LAUNCHER`, `NO_SMS_APP`, `NO_MAIL_APP`, `NO_BROWSER_APP`, `SYSTEM_WINDOW_REQUIRES_USER`, and `TARGET_APP_TIMEOUT` are real failures; do not infer that an app opened. `SYSTEM_WINDOW_REQUIRES_USER` means Android placed a protected system window above the app. First verify that the helper truly cannot read or operate it; only then ask the owner to handle that visible gate and resume with `observe`.

`PHONE_CONTROL_UNAVAILABLE` means the local helper connection failed; it does not prove that Accessibility was disabled. Pause mutations and retry only read-only `status` up to three times, with short increasing delays (1, 2, 4 seconds). If `READY` returns, inspect a fresh `observe`, verify whether the previous action already took effect, and resume without repeating an ambiguous action. If all checks fail, tell the owner: “Nie mogę teraz połączyć się ze sterowaniem telefonu. Otwórz Mobile Bot i sprawdź połączenie; jeśli aplikacja pokaże Włącz, zezwól na sterowanie.” Do not claim the service is switched off without explicit evidence. Android does not allow this app to enable its own Accessibility service. After the owner returns, check `status` again and resume the original task.

Installed, logged in, and readable are three separate states. An entry in `list-apps` proves only that Android exposes a launcher activity. Confirm account state from the visible app. Confirm readability from a fresh observation; sensitive screens and password fields may be unavailable or masked, and `SENSITIVE_TARGET` rejects text entry into a password field. Never enter, extract, or report passwords or authentication secrets.

Prefer visible semantic controls and the smallest action that advances the task. Confirm the target account, item, or setting from the screen before any action with an external effect. Do not infer success from a click alone; verify the resulting screen or device state.

Complete the requested flow independently. Continue through ordinary navigation, informational screens, reversible setup steps, and permissions or confirmations covered by the owner's request. Do not hand the task back merely because the interface is unfamiliar, has several steps, or shows a routine choice. Ask the owner only for protected input or a decision the helper cannot make: password, device PIN, passkey, OTP, CAPTCHA, an ambiguous account or recipient, or an unreadable protected Android window. After the owner completes that gate, resume from a fresh `observe` and finish the flow.

After every `click`, `set-text`, `scroll`, or `back`, inspect the returned observation before continuing. This applies to browsers, SMS, email, WhatsApp, and other launchable apps. Routine navigation and read-only inspection are allowed within the requested task. Sending a message or email, purchasing, deleting, publishing, changing accounts, or accepting terms requires the user's current request to cover that effect. Preparing text does not authorize sending it. Report the last verified state when the phone is locked, unavailable, or the expected control is absent.

Observation nodes may identify an editable field through `text`, `description`, `hintText`, or `viewId`. If the visible label is a separate node and the intended editable field has no attached selector, first inspect a fresh observation and confirm the field's zero-based position among visible `editable: true` nodes. Only then use an explicit empty selector with that index: `set-text "" "VALUE" INDEX`. Never use an empty selector before confirming the target field and index from the current screen.

Before operating a common communication, productivity, media, or system app, read [references/app-playbooks.md](references/app-playbooks.md). The playbooks describe stable entry points and required readback, but the current visible UI remains authoritative.
