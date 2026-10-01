---
name: allegro-price-monitor
description: Search Allegro through the real phone browser, compare equivalent offers by total visible price, and register a recurring 15-minute check. Use when the shopping agent is asked to find or monitor the lowest price of a product on Allegro.
---

# Allegro Price Monitor

Use the installed phone browser through the local helper at `$HOME/.mobile-bot-ui/phone-client.mjs`. Do not call an Allegro API, scrape a background HTTP endpoint, or claim a result that was not visible in the browser UI.

For a new monitoring request:

1. Extract a concrete product identity and required variant from the user's wording. If a detail changes product equivalence, ask for it instead of mixing variants.
2. Register exactly one recurring job with `$HOME/.mobile-bot-ui/automation-client.mjs`. The interval is always 15 minutes. The scheduled prompt must contain the normalized product query, equivalence rules, and the instruction to use this skill's phone workflow. Reusing the same request is idempotent.
3. Perform the first check immediately; do not wait 15 minutes for the first result.

For every check, including scheduled ones:

1. Run `node "$HOME/.mobile-bot-ui/phone-client.mjs" status`. If the phone is locked or the service is unavailable, report that exact state. Do not ask the user to click through the shopping flow.
2. URL-encode the query and open `https://allegro.pl/listing?string=<query>&order=p` with `open-url`.
3. Wait for the listing to settle, then use `observe`. Use one purposeful `click` or `scroll` only when the visible state requires it, and inspect the returned fresh observation before choosing another action. Never loop on reload, the same control, or rapid scrolling.
4. If the visible page reports automated traffic, bot protection, access denied, unusual activity, a security check, CAPTCHA, or an incident/reference ID, stop the current check as `BLOCKED_ANTI_BOT`. Record the visible reason and reference ID, if present. Do not reload or retry within that run; keep the automation active for a later scheduled cycle. A CAPTCHA or protected browser challenge is the only point to ask the owner for help.
5. Compare offers only when title, model, size/capacity, condition, quantity, and other user-specified variants are equivalent. Prefer the lowest visible total of item price plus delivery. If delivery is absent, label the comparison as item-price-only.
6. Record the timestamp, normalized query, offer title, item price, delivery/total when visible, and the visible evidence used. Never purchase, add to cart, contact a seller, or accept a paid commitment.

The owner grants standing authorization for this skill to open the browser, search Allegro, read visible offer data, scroll/click non-transactional search controls, and repeat the approved query every 15 minutes. This authorization does not include purchases, cart changes, seller contact, account changes, or accepting terms with a financial effect.

Read [references/phone-workflow.md](references/phone-workflow.md) for the exact helper commands and result states.
