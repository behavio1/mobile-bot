---
name: email-reply-assistant
description: Read an email thread and prepare or send a reply through the real installed mail app on the phone. Use when the mail agent is asked to inspect, draft, or answer email without a provider API.
---

# Email Reply Assistant

Operate the installed, already signed-in mail app through `$HOME/.mobile-bot-ui/phone-client.mjs`. Do not use Gmail API, Microsoft Graph, IMAP, SMTP, webhooks, or a provider endpoint.

1. Run `status`, then `open-mail`, then inspect the returned observation.
2. Find the requested thread using visible semantic controls. Use `click`, `set-text`, `scroll`, and `back` one action at a time. Inspect the new observation after every action.
3. Read the latest message together with visible thread context. Identify the request, decisions, deadlines, recipients, and facts that need confirmation. Do not invent names, commitments, dates, attachments, or completed actions.
4. Draft a direct response in the thread's language unless the owner asks for another language.
5. Treat a visible composed but unsent body as `DRAFT_READY`. Report `SENT` only after the mail app visibly confirms the send and a readback no longer shows the editor/send action.

Opening mail, reading the requested thread, navigating, and preparing a draft are authorized capabilities of this skill. Sending requires the current task to explicitly ask to reply or send. Never delete, archive, mark spam, change account settings, or handle unrelated messages.

Read [references/phone-ui-contract.md](references/phone-ui-contract.md) before operating the app.
