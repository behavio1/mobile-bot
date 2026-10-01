---
name: change-monitoring
description: Check a defined source repeatedly, detect meaningful changes, and report evidence. Use when the Atlas agent is asked to monitor a page, value, availability, or published information.
---

# Change Monitoring

Define the monitored target, the fields that count as meaningful, the comparison baseline, and the requested cadence. Reuse an existing equivalent monitor when one already exists. A check is successful only when the target source was read and compared with the last verified state.

For each run, record the observation time, source, observed values, previous values, and whether a meaningful change occurred. Ignore layout noise and unrelated page changes. If the source is unavailable or ambiguous, report the failed observation without replacing the last known good baseline.

Notify the owner only according to the requested condition. Include the old and new values plus the evidence needed to verify the change. Never describe an unchanged or failed check as a detected change.
