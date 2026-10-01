# Phone workflow

All commands return one JSON object and a fresh bounded observation where relevant.

```sh
node "$HOME/.mobile-bot-ui/phone-client.mjs" status
node "$HOME/.mobile-bot-ui/phone-client.mjs" open-url "https://allegro.pl/listing?string=QUERY&order=p"
node "$HOME/.mobile-bot-ui/phone-client.mjs" observe
node "$HOME/.mobile-bot-ui/phone-client.mjs" click "VISIBLE TEXT" 0
node "$HOME/.mobile-bot-ui/phone-client.mjs" scroll forward
node "$HOME/.mobile-bot-ui/phone-client.mjs" back
```

Register the recurring check from the first interactive run:

```sh
node "$HOME/.mobile-bot-ui/automation-client.mjs" create \
  --name "Cena: NORMALIZED QUERY" \
  --prompt "Sprawdź teraz NORMALIZED QUERY na Allegro. Użyj skilla allegro-price-monitor, nie twórz kolejnej automatyzacji i zwróć najniższą potwierdzoną cenę." \
  --interval-minutes 15
```

The helper obtains the current agent and conversation identifiers from the Host-created environment. Never write those identifiers into a reusable skill file.

Result states:

- `READY`: the Accessibility executor is connected and the screen can be operated.
- `WAITING_FOR_UNLOCK`: keep the automation active and report the skipped check; the next 15-minute cycle will retry.
- `SERVICE_DISABLED`: the initial one-time Android accessibility setup has not been completed.
- `NO_ACTIVE_WINDOW` or `TARGET_NOT_FOUND`: the UI could not be read or the requested control was absent; do not infer success.
- `BLOCKED_ANTI_BOT`: the visible page refused automated traffic or requires a protected challenge. Preserve the page, record the visible reason/reference ID, and end this run without reload or immediate retry. The next scheduled cycle may try again; ask the owner only for CAPTCHA or another protected login challenge.
