import { readFileSync } from "node:fs";

const installRoot = `${process.env.HOME ?? "."}/.mobile-bot-ui`;
const [command, ...args] = process.argv.slice(2);
const readOnly = process.env.MOBILE_BOT_PHONE_READ_ONLY === "1";

function usage(message) {
  process.stderr.write(`${message}\n`);
  process.exit(64);
}

function requestPayload() {
  switch (command) {
    case "status": return { action: "status" };
    case "observe": return { action: "observe" };
    case "list-apps": return { action: "list_apps", ...(args.length ? { query: args.join(" ") } : {}) };
    case "home": return { action: "home" };
    case "open-app":
      if (!args[0]) usage("open-app requires a package ID from list-apps");
      return { action: "open_app", packageName: args[0] };
    case "open-url":
      if (!args[0]) usage("open-url requires an http or https URL");
      return { action: "open_url", url: args[0] };
    case "open-mail": return { action: "open_mail" };
    case "open-sms": return { action: "open_sms" };
    case "click":
      if (!args[0]) usage("click requires visible text");
      return { action: "click", text: args[0], index: Number.parseInt(args[1] ?? "0", 10), ...(readOnly ? { readOnly: true } : {}) };
    case "set-text":
      if (args[0] === undefined || args[1] === undefined) {
        usage("set-text requires a field selector (which may be empty) and value");
      }
      return {
        action: "set_text",
        selector: args[0],
        text: args[1],
        index: Number.parseInt(args[2] ?? "0", 10),
        ...(readOnly ? { readOnly: true } : {}),
      };
    case "scroll":
      return { action: "scroll", direction: args[0] === "backward" ? "backward" : "forward" };
    case "back": return { action: "back" };
    default:
      usage("commands: status, observe, list-apps, open-app, open-url, open-mail, open-sms, click, set-text, scroll, home, back");
  }
}

function readDeviceToken() {
  try {
    const token = readFileSync(`${installRoot}/device-token`, "utf8").trim();
    return token || null;
  } catch {
    return null;
  }
}

async function callDevice(payload, token, traceId) {
  let response;
  try {
    response = await fetch("http://127.0.0.1:8768/v1/actions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json; charset=utf-8",
        ...(traceId ? { "X-Mobile-Bot-Trace-Id": traceId } : {}),
        ...(process.env.MOBILE_BOT_RUN_ID ? { "X-Mobile-Bot-Run-Id": process.env.MOBILE_BOT_RUN_ID } : {}),
        ...(process.env.MOBILE_BOT_AUTOMATION_ID ? {
          "X-Mobile-Bot-Automation-Id": process.env.MOBILE_BOT_AUTOMATION_ID,
        } : {}),
        ...(process.env.MOBILE_BOT_AGENT_ID ? {
          "X-Mobile-Bot-Agent-Id": process.env.MOBILE_BOT_AGENT_ID,
        } : {}),
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(20_000),
    });
  } catch (error) {
    const transportCode = error && typeof error === "object" && error.cause
      && typeof error.cause === "object" ? error.cause.code : undefined;
    return {
      ok: false,
      state: error instanceof Error && error.name === "TimeoutError"
        ? "NETWORK_TIMEOUT"
        : ["ECONNREFUSED", "EHOSTUNREACH", "ENETUNREACH"].includes(transportCode)
          ? "PHONE_CONTROL_UNAVAILABLE"
          : "NETWORK_ERROR",
    };
  }

  if (response.status === 401) return { ok: false, state: "AUTHORIZATION_FAILED", httpStatus: 401 };
  if (response.status === 403) return { ok: false, state: "PERMISSION_DENIED", httpStatus: 403 };
  let text;
  try {
    text = await response.text();
  } catch (error) {
    return {
      ok: false,
      state: error instanceof Error && ["TimeoutError", "AbortError"].includes(error.name)
        ? "NETWORK_TIMEOUT"
        : "NETWORK_ERROR",
    };
  }
  if (!text.trim()) return { ok: false, state: "EMPTY_RESPONSE", httpStatus: response.status };
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, state: "PROTOCOL_ERROR", httpStatus: response.status };
  }
  if (
    !parsed
    || typeof parsed !== "object"
    || Array.isArray(parsed)
    || typeof parsed.ok !== "boolean"
    || typeof parsed.state !== "string"
    || !parsed.state.trim()
  ) {
    return { ok: false, state: "PROTOCOL_ERROR", httpStatus: response.status };
  }
  if (!response.ok) return { ok: false, state: "HTTP_ERROR", httpStatus: response.status };
  return parsed;
}

async function notifyHost(payload, result, traceId) {
  const runId = process.env.MOBILE_BOT_RUN_ID;
  const token = readDeviceToken();
  if (!runId || !token) return;
  await fetch("http://127.0.0.1:8767/v5/executor/events", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json; charset=utf-8",
      "X-Mobile-Bot-Trace-Id": traceId || runId,
      ...(process.env.MOBILE_BOT_AGENT_ID ? {
        "X-Mobile-Bot-Agent-Id": process.env.MOBILE_BOT_AGENT_ID,
      } : {}),
    },
    body: JSON.stringify({
      runId,
      action: payload.action,
      ok: result.ok === true,
      state: String(result.state ?? "UNKNOWN"),
    }),
    signal: AbortSignal.timeout(3_000),
  }).catch(() => undefined);
}

const payload = requestPayload();
const token = readDeviceToken();
const traceId = process.env.MOBILE_BOT_TRACE_ID || process.env.MOBILE_BOT_RUN_ID;
const result = token
  ? await callDevice(payload, token, traceId)
  : { ok: false, state: "AUTHORIZATION_UNAVAILABLE" };
await notifyHost(payload, result, traceId);
process.stdout.write(`${JSON.stringify(result)}\n`);
