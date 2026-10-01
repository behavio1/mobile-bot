import { spawn } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { LOCAL_AI_CONTEXT_WINDOW, LOCAL_AI_MODEL, LOCAL_AI_PI } from "./localAi.js";
const MODEL_ID = "mobile-bot-qwen35-08b-q4km";
const READ_ONLY_PHONE_SYSTEM_PROMPT = [
    "Jesteś lokalnym agentem Mobile Bot.",
    "Użyj `phone_status`, potem `phone_observe`. Kliknij dokładną etykietę celu, gdy jest widoczna. Jeśli celu nie ma w `nodes`, lecz ekran jest przewijalny, przewiń. Jeśli `perception.supportRecommended=true` i potrzebnej treści nie ma w `nodes`, użyj `phone_request_perception_support`. Gdy cel jest potwierdzony, wywołaj `phone_finish` z 1–4 pełnymi etykietami lub wartościami z ostatniego odczytu. Nie skracaj ani nie powtarzaj dowodów. Po błędzie nie powtarzaj tej samej akcji. Tylko odczyt.",
].join("\n\n");
function safePart(value) {
    return value.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 120) || "conversation";
}
function textFromContent(content) {
    if (typeof content === "string")
        return content;
    if (!Array.isArray(content))
        return "";
    return content
        .map((part) => {
        if (!part || typeof part !== "object")
            return "";
        const value = part;
        return value.type === "text" && typeof value.text === "string"
            ? value.text
            : typeof value.content === "string" ? value.content : "";
    })
        .filter(Boolean)
        .join("\n");
}
function errorCode(error, fallback) {
    const message = error instanceof Error ? error.message : "";
    return /^[a-z][a-z0-9_]{1,80}$/.test(message) ? message : fallback;
}
function cleanModelReply(value) {
    return value.replace(/<think>[\s\S]*?<\/think>\s*/gi, "").trim();
}
function prohibitsPhoneTools(prompt) {
    const value = prompt.toLowerCase();
    return [
        "nie używaj narzędzi telefonu",
        "bez używania narzędzi telefonu",
        "do not use phone tools",
        "don't use phone tools",
    ].some((phrase) => value.includes(phrase));
}
function requestsReadOnlyPhoneUse(prompt) {
    const value = prompt.toLowerCase();
    return [
        "tylko odczyt",
        "tylko z odczytu",
        "wyłącznie odczyt",
        "niczego nie zmieniaj",
        "nie zmieniaj ustawień",
        "bez zmieniania",
        "read-only",
        "read only",
        "do not change",
        "don't change",
    ].some((phrase) => value.includes(phrase));
}
function explicitSettingsTarget(prompt) {
    const lower = prompt.toLocaleLowerCase("pl");
    const mentionsSettings = lower.includes("ustawien") || /\bsettings\b/i.test(prompt);
    const requestsNavigation = /(otwórz|otworz|wejdź|wejdz|przejdź|przejdz|\bopen\b|\bgo to\b)/i.test(prompt);
    if (!mentionsSettings || !requestsNavigation)
        return null;
    const candidates = [...prompt.matchAll(/\(([^()\n]{2,80})\)/g)]
        .flatMap((match) => typeof match[1] === "string" ? [match[1].trim()] : [])
        .filter((value) => /^[A-Za-z0-9][A-Za-z0-9 &'./+\-]{1,79}$/.test(value));
    const unique = [...new Map(candidates.map((value) => [value.toLowerCase(), value])).values()];
    return unique.length === 1 ? (unique[0] ?? null) : null;
}
export class PiLocalRuntime {
    localAi;
    root;
    piHome;
    extensionPath;
    phoneClientPath;
    constructor(localAi, installRoot) {
        this.localAi = localAi;
        this.root = join(installRoot, "local-ai");
        this.piHome = join(this.root, "pi-home");
        this.extensionPath = join(this.root, "phone-tools.mjs");
        this.phoneClientPath = join(installRoot, "phone-client.mjs");
        mkdirSync(this.piHome, { recursive: true, mode: 0o700 });
        this.writeProviderConfig();
        this.writeSettings();
        this.writePhoneExtension();
    }
    async run(input) {
        await this.localAi.start();
        const workspace = join(this.root, "sessions", safePart(input.conversationId), "workspace");
        mkdirSync(join(workspace, ".pi"), { recursive: true, mode: 0o700 });
        writeFileSync(join(workspace, ".pi", "SYSTEM.md"), this.systemPrompt(input), { mode: 0o600 });
        const sessionDir = join(this.root, "sessions", safePart(input.conversationId));
        mkdirSync(sessionDir, { recursive: true, mode: 0o700 });
        const args = [
            "--mode", "rpc",
            "--provider", "mobile-bot-local",
            "--model", MODEL_ID,
            "--thinking", "off",
            "--no-builtin-tools",
            "--no-extensions",
            "--extension", this.extensionPath,
            "--session-dir", sessionDir,
            "--approve",
        ];
        if (input.threadId && existsSync(input.threadId))
            args.push("--session", input.threadId);
        const apiKey = this.localAi.apiKey;
        const explicitTarget = requestsReadOnlyPhoneUse(input.prompt)
            ? explicitSettingsTarget(input.prompt)
            : null;
        if (!apiKey)
            throw new Error("local_ai_api_key_missing");
        return await new Promise((resolve, reject) => {
            const child = spawn("pi", args, {
                cwd: workspace,
                env: {
                    ...process.env,
                    PI_CODING_AGENT: "1",
                    PI_CODING_AGENT_DIR: this.piHome,
                    PI_OFFLINE: "1",
                    MOBILE_BOT_PHONE_CLIENT: this.phoneClientPath,
                    MOBILE_BOT_AGENT_ID: input.agent.id,
                    MOBILE_BOT_CONVERSATION_ID: input.conversationId,
                    MOBILE_BOT_RUN_ID: input.runId,
                    MOBILE_BOT_TRACE_ID: input.traceId,
                    MOBILE_BOT_LOCAL_API_KEY: apiKey,
                    MOBILE_BOT_LOCAL_API_URL: `http://127.0.0.1:${this.localAi.port}/v1`,
                    MOBILE_BOT_LOCAL_TOOL_LOG: join(sessionDir, "phone-tool-results.ndjson"),
                    ...(prohibitsPhoneTools(input.prompt) ? { MOBILE_BOT_DISABLE_PHONE_TOOLS: "1" } : {}),
                    ...(requestsReadOnlyPhoneUse(input.prompt) ? { MOBILE_BOT_PHONE_READ_ONLY: "1" } : {}),
                    ...(explicitTarget ? { MOBILE_BOT_EXPLICIT_SETTINGS_TARGET: explicitTarget } : {}),
                    ...(input.threadId ? { MOBILE_BOT_ENABLE_LEGACY_PHONE_ACTION: "1" } : {}),
                    ...(input.automationId ? { MOBILE_BOT_AUTOMATION_ID: input.automationId } : {}),
                },
                stdio: ["pipe", "pipe", "pipe"],
            });
            let buffer = "";
            let sessionFile = input.threadId;
            let reply = "";
            let lastToolFailure = "";
            let terminalToolReply = "";
            let accepted = false;
            let settled = false;
            let finishing = false;
            let finalError;
            let stderrBytes = 0;
            let timeoutTimer;
            let terminateTimer = null;
            let killTimer = null;
            const finalize = (error) => {
                if (settled)
                    return;
                settled = true;
                clearTimeout(timeoutTimer);
                if (terminateTimer)
                    clearTimeout(terminateTimer);
                if (killTimer)
                    clearTimeout(killTimer);
                if (error)
                    reject(error);
                else if (sessionFile && reply.trim())
                    resolve({ threadId: sessionFile, reply: reply.trim() });
                else
                    reject(new Error("pi_empty_response"));
            };
            const finish = (error, abort = false) => {
                if (settled || finishing)
                    return;
                finishing = true;
                finalError = error;
                clearTimeout(timeoutTimer);
                if (abort && child.stdin.writable) {
                    child.stdin.write('{"type":"clear_queue"}\n{"type":"abort"}\n');
                }
                child.stdin.end();
                terminateTimer = setTimeout(() => {
                    if (!child.killed)
                        child.kill("SIGTERM");
                    killTimer = setTimeout(() => {
                        if (!child.killed)
                            child.kill("SIGKILL");
                    }, 2_000);
                    killTimer.unref();
                }, 2_000);
                terminateTimer.unref();
            };
            const consume = (line) => {
                if (!line.trim())
                    return;
                let event;
                try {
                    event = JSON.parse(line);
                }
                catch {
                    return;
                }
                if (event.type === "response") {
                    if (event.id === "state" && event.success === true) {
                        const data = event.data;
                        if (typeof data?.sessionFile === "string")
                            sessionFile = data.sessionFile;
                    }
                    if (event.id === "prompt" && event.success === true)
                        accepted = true;
                    if (event.id === "prompt" && event.success === false)
                        finish(new Error("pi_prompt_rejected"));
                }
                if (event.type === "message_end") {
                    const message = event.message;
                    if (message?.role === "assistant") {
                        const text = textFromContent(message.content);
                        if (text.trim())
                            reply = cleanModelReply(text);
                    }
                }
                if (event.type === "tool_execution_end") {
                    const result = event.result;
                    const text = textFromContent(result?.content);
                    try {
                        const failure = JSON.parse(text);
                        if (failure.ok === false && typeof failure.state === "string") {
                            lastToolFailure = `Nie udało się wykonać zadania: ${failure.state}.${typeof failure.detail === "string" ? ` ${failure.detail}` : ""}`;
                        }
                        else {
                            lastToolFailure = "";
                            if (failure.ok === true && ["GOAL_REPORTED", "PERCEPTION_SUPPORT_REQUIRED"].includes(String(failure.state)) && typeof failure.summary === "string") {
                                terminalToolReply = failure.summary;
                            }
                        }
                    }
                    catch {
                        lastToolFailure = "";
                    }
                }
                if (event.type === "agent_settled") {
                    if (!accepted)
                        finish(new Error("pi_prompt_not_accepted"));
                    else {
                        if (terminalToolReply.trim())
                            reply = terminalToolReply;
                        else if (!reply.trim() && lastToolFailure)
                            reply = lastToolFailure;
                        finish();
                    }
                }
                if (event.type === "agent_error" || event.type === "error")
                    finish(new Error("pi_agent_failed"));
            };
            child.stdout.setEncoding("utf8");
            child.stdout.on("data", (chunk) => {
                buffer += chunk;
                let newline = buffer.indexOf("\n");
                while (newline >= 0) {
                    consume(buffer.slice(0, newline));
                    buffer = buffer.slice(newline + 1);
                    newline = buffer.indexOf("\n");
                }
                if (buffer.length > 2_097_152)
                    finish(new Error("pi_output_too_large"));
            });
            child.stderr.on("data", (chunk) => { stderrBytes += chunk.length; });
            child.on("error", (error) => {
                const failure = new Error(errorCode(error, "pi_process_failed"));
                if (finishing)
                    finalError ??= failure;
                else
                    finalize(failure);
            });
            child.on("close", (code, signal) => {
                if (buffer.trim())
                    consume(buffer);
                if (settled)
                    return;
                if (finishing)
                    finalize(finalError);
                else if (code === 0 && reply.trim())
                    finalize();
                else
                    finalize(new Error(`pi_exit_${code ?? "null"}_${signal ?? "null"}_${stderrBytes}`));
            });
            timeoutTimer = setTimeout(() => {
                finish(new Error("pi_timeout"), true);
            }, requestsReadOnlyPhoneUse(input.prompt)
                ? 2 * 60_000
                : input.agent.skills.some((skill) => skill.id === "android-app-builder") ? 45 * 60_000 : 10 * 60_000);
            child.stdin.write('{"id":"state","type":"get_state"}\n');
            child.stdin.write(JSON.stringify({ id: "prompt", type: "prompt", message: input.prompt }) + "\n");
        });
    }
    systemPrompt(input) {
        if (requestsReadOnlyPhoneUse(input.prompt)) {
            const explicitTarget = explicitSettingsTarget(input.prompt);
            return explicitTarget
                ? `${READ_ONLY_PHONE_SYSTEM_PROMPT}\nJawny cel tego zadania to „${explicitTarget}”. Na głównym ekranie Settings kliknij tę etykietę albo jej jednoznaczne rozszerzenie widoczne w nodes; jeśli celu nie ma w nodes, użyj phone_scroll.`
                : READ_ONLY_PHONE_SYSTEM_PROMPT;
        }
        const skills = input.agent.skills.map((skill) => `- ${skill.name}: ${skill.summary}`).join("\n");
        return [
            `Jesteś agentem Mobile Bot o nazwie „${input.agent.name}”.`,
            input.agent.systemPrompt,
            input.agent.ownerProfile ? `Informacje właściciela: ${input.agent.ownerProfile}` : "",
            "Masz dostęp do osobnych narzędzi telefonu. Używaj ich tylko wtedy, gdy zadanie wymaga rzeczywistego działania lub odczytu na telefonie.",
            "Dla zadania na telefonie zawsze pracuj kolejno: (1) phone_status, (2) phone_observe, (3) jedna akcja na elemencie widocznym w wyniku, (4) ponowny phone_observe lub kompletny readback akcji. Odpowiedź oprzyj wyłącznie na ostatnim potwierdzonym ekranie.",
            "Po phone_observe sprawdź perception. Jeśli supportRecommended=true, a cel dotyczy treści, której nie ma w nodes, natychmiast wywołaj phone_request_perception_support. Nie klikaj innej kontrolki i nie zgaduj treści obrazu. Jeśli żądana kontrolka ma dokładną etykietę w nodes, możesz nadal użyć tej etykiety bez wsparcia obrazu.",
            "Dla sekcji Ustawień najpierw odczytaj bieżący ekran. Jeśli właściwa etykieta jest widoczna, użyj phone_click. Jeśli Ustawienia nie są otwarte, znajdź pakiet Settings przez phone_list_apps i otwórz go przez phone_open_app. phone_open_url służy tylko do pełnych adresów http:// lub https://; nazwa ekranu albo ustawienia nie są adresem WWW.",
            "Gdy tytuł ekranu lub readback akcji potwierdza żądaną sekcję i zawiera informacje potrzebne do odpowiedzi, natychmiast zakończ narzędzia i odpowiedz. Nie otwieraj pozycji znajdujących się na tym ekranie, jeśli użytkownik prosi tylko o ich odczyt.",
            "phone_finish jest jedynym poprawnym sposobem zakończenia zadania telefonu. Gdy bieżący ekran potwierdza cel, natychmiast wywołaj phone_finish z 1–4 dokładnymi etykietami lub wartościami z tego ekranu jako evidence. Podsumowanie utworzy harness. Nie wykonuj wtedy następnego kliknięcia. Nie wywołuj phone_finish, dopóki bieżący ekran nie potwierdza celu.",
            "Ograniczenia użytkownika są częścią celu: słowa „bez”, „nie”, „niczego nie zmieniaj” i „tylko odczyt” zabraniają wejścia w działanie opisane po tych słowach. Nawigacja do żądanego ekranu jest dozwolona, lecz nie wykonuj następnego działania z listy.",
            "Nie przechodź na ekran główny ani nie otwieraj Mobile Bot, jeśli zadanie tego nie wymaga. Nie klikaj tekstu, którego nie ma w ostatnim odczycie ekranu.",
            "Po błędzie odczytaj aktualny ekran i zmień akcję lub argumenty. Nie powtarzaj tej samej nieskutecznej próby ani nie zwiększaj indeksu bez dowodu, że istnieje kolejny pasujący element.",
            "Nie twierdź, że coś zostało wykonane bez wyniku ok=true, właściwego pakietu/tytułu i żądanej wartości odczytanej z telefonu. Gdy celu nie da się osiągnąć, podaj kod błędu i ostatni potwierdzony ekran.",
            "Końcowa odpowiedź ma opisywać wykonany odczyt lub konkretną przeszkodę. Nie wydawaj użytkownikowi polecenia wykonania czynności, którą agent miał wykonać sam.",
            "Nie wysyłaj wiadomości ani nie wykonuj nieodwracalnych akcji bez jednoznacznego polecenia użytkownika.",
            skills ? `Przypisane moce:\n${skills}` : "",
            "Odpowiadaj w języku wiadomości użytkownika, krótko i konkretnie.",
        ].filter(Boolean).join("\n\n");
    }
    writeProviderConfig() {
        const modelsPath = join(this.piHome, "models.json");
        writeFileSync(modelsPath, JSON.stringify({
            providers: {
                "mobile-bot-local": {
                    baseUrl: `http://127.0.0.1:${this.localAi.port}/v1`,
                    api: "openai-completions",
                    apiKey: "$MOBILE_BOT_LOCAL_API_KEY",
                    models: [{
                            id: MODEL_ID,
                            name: LOCAL_AI_MODEL.name,
                            reasoning: false,
                            input: ["text"],
                            cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
                            contextWindow: LOCAL_AI_CONTEXT_WINDOW,
                            maxTokens: 1024,
                        }],
                },
            },
        }), { mode: 0o600 });
    }
    writeSettings() {
        writeFileSync(join(this.piHome, "settings.json"), JSON.stringify({
            compaction: {
                enabled: true,
                reserveTokens: 2048,
                keepRecentTokens: 4096,
            },
            quietStartup: true,
            enableInstallTelemetry: false,
            enableAnalytics: false,
        }), { mode: 0o600 });
    }
    writePhoneExtension() {
        const source = `
import { spawn } from "node:child_process";
import { appendFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";
import { Type } from "typebox";

const run = (args, signal) => new Promise((resolve, reject) => {
  const child = spawn(process.execPath, [process.env.MOBILE_BOT_PHONE_CLIENT, ...args], { stdio: ["ignore", "pipe", "pipe"], signal });
  let out = ""; let err = "";
  child.stdout.setEncoding("utf8"); child.stderr.setEncoding("utf8");
  child.stdout.on("data", (chunk) => { out += chunk; }); child.stderr.on("data", (chunk) => { err += chunk; });
  child.on("error", reject); child.on("close", (code) => code === 0 ? resolve(out.trim()) : reject(new Error("phone_action_failed")));
});

let screenFingerprint = "unknown";
const failedCalls = new Map();
const noProgressCalls = new Map();
const successfulTransitions = new Set();
let visibleEvidence = new Map();
let currentEvidenceCandidates = [];
let currentScreen = null;
let recoveryObservationRequired = false;
const empty = Type.Object({}, { additionalProperties: false });
const string = (description) => Type.String({ minLength: 1, description });
const normalizeSelector = (value) => String(value)
  .trim()
  .replace(/^(?:text|label|description)\\s*:\\s*/i, "")
  .replace(/^[\"']|[\"']$/g, "")
  .trim();

const toolError = (state, detail) => new Error(JSON.stringify({ ok: false, state, detail }));
const persistRawResult = (toolName, raw) => {
  const path = process.env.MOBILE_BOT_LOCAL_TOOL_LOG;
  if (!path) return;
  try {
    mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
    appendFileSync(path, JSON.stringify({ timestamp: new Date().toISOString(), toolName, raw }) + "\\n", { mode: 0o600 });
  } catch {}
};
const cleanNode = (node) => {
  if (!node || typeof node !== "object") return null;
  const text = typeof node.text === "string" ? node.text : "";
  const description = typeof node.description === "string" ? node.description : "";
  const hintText = typeof node.hintText === "string" ? node.hintText : "";
  const viewId = typeof node.viewId === "string" ? node.viewId : "";
  const clickable = node.clickable === true;
  const editable = node.editable === true;
  const scrollable = node.scrollable === true;
  if (!text && !description && !hintText && !editable && !scrollable && !(clickable && viewId)) return null;
  return {
    ...(text ? { text } : {}),
    ...(description && description !== text ? { description } : {}),
    ...(hintText ? { hintText } : {}),
    ...(viewId ? { viewId } : {}),
    ...(clickable ? { clickable: true } : {}),
    ...(editable ? { editable: true } : {}),
    ...(scrollable ? { scrollable: true } : {}),
    ...(typeof node.bounds === "string" ? { bounds: node.bounds } : {}),
  };
};
const compactResult = (value, maxChars = 7000) => {
  const result = {
    ok: value.ok === true,
    state: typeof value.state === "string" ? value.state : "UNKNOWN",
  };
  for (const key of ["matchCount", "appCount", "totalAppCount", "query", "protocolVersion"]) {
    if (value[key] !== undefined) result[key] = value[key];
  }
  if (Array.isArray(value.apps)) result.apps = value.apps;
  if (value.observation && typeof value.observation === "object") {
    const observation = value.observation;
    const allNodes = Array.isArray(observation.nodes) ? observation.nodes : [];
    const nodes = allNodes.map(cleanNode).filter(Boolean);
    result.observation = {
      ok: observation.ok === true,
      state: typeof observation.state === "string" ? observation.state : "UNKNOWN",
      packageName: typeof observation.packageName === "string" ? observation.packageName : "",
      windowTitle: typeof observation.windowTitle === "string" ? observation.windowTitle : "",
      nodes,
      readableNodeCount: observation.readableNodeCount,
      returnedNodeCount: nodes.length,
      omittedNodeCount: allNodes.length - nodes.length,
      truncated: observation.truncated === true,
      ...(observation.perception && typeof observation.perception === "object"
        ? { perception: observation.perception }
        : {}),
    };
  }
  let serialized = JSON.stringify(result);
  const nodes = result.observation?.nodes;
  while (serialized.length > maxChars && Array.isArray(nodes) && nodes.length > 0) {
    nodes.pop();
    result.observation.omittedNodeCount += 1;
    result.observation.returnedNodeCount = nodes.length;
    result.observation.truncated = true;
    serialized = JSON.stringify(result);
  }
  const apps = result.apps;
  while (serialized.length > maxChars && Array.isArray(apps) && apps.length > 0) {
    apps.pop();
    result.omittedAppCount = (result.omittedAppCount || 0) + 1;
    result.truncated = true;
    serialized = JSON.stringify(result);
  }
  if (serialized.length > maxChars) throw toolError("PHONE_RESULT_TOO_LARGE", "Wynik telefonu nie mieści się w bezpiecznym kontekście.");
  return serialized;
};
const fingerprint = (value) => {
  const observation = value && typeof value === "object" ? value.observation : null;
  if (!observation || typeof observation !== "object") return null;
  return JSON.stringify({
    packageName: observation.packageName || "",
    windowTitle: observation.windowTitle || "",
    labels: Array.isArray(observation.nodes)
      ? observation.nodes.slice(0, 30).map((node) => [node?.text || "", node?.description || "", node?.hintText || ""])
      : [],
  });
};
const updateVisibleEvidence = (value) => {
  const observation = value && typeof value === "object" ? value.observation : null;
  if (!observation || typeof observation !== "object" || !Array.isArray(observation.nodes)) return;
  const next = new Map();
  const candidates = [];
  const candidateKeys = new Set();
  for (const node of observation.nodes) {
    for (const field of [node?.text, node?.description, node?.hintText]) {
      if (typeof field !== "string" || !field.trim()) continue;
      const key = field.trim().toLowerCase();
      const entry = next.get(key) || { value: field.trim(), count: 0 };
      entry.count += 1;
      next.set(key, entry);
      if (!candidateKeys.has(key)) {
        candidates.push(field.trim());
        candidateKeys.add(key);
      }
    }
  }
  visibleEvidence = next;
  currentEvidenceCandidates = candidates;
  currentScreen = {
    packageName: typeof observation.packageName === "string" ? observation.packageName : "",
    windowTitle: typeof observation.windowTitle === "string" ? observation.windowTitle : "",
    perception: observation.perception && typeof observation.perception === "object" ? observation.perception : null,
    scrollable: observation.nodes.some((node) => node?.scrollable === true),
  };
};
const executePhone = async (toolName, args, signal) => {
  const signature = JSON.stringify([toolName, args]);
  const transitionKey = JSON.stringify([screenFingerprint, toolName, args]);
  if (toolName !== "phone_observe" && failedCalls.get(signature) === screenFingerprint) {
    return {
      content: [{ type: "text", text: JSON.stringify({ ok: false, state: "REPEATED_FAILED_ACTION", detail: "Zatrzymano identyczną nieudaną akcję. Odczytaj ekran i wybierz inną akcję." }) }],
      terminate: true,
    };
  }
  const explicitTarget = normalizeSelector(process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET || "").toLowerCase();
  const onSettingsRoot = currentScreen?.packageName === "com.android.settings"
    && currentScreen?.windowTitle?.toLowerCase() === "settings";
  const matchingTargets = explicitTarget && onSettingsRoot
    ? [...visibleEvidence.keys()].filter(
      (value) => value === explicitTarget || value.startsWith(explicitTarget + " "),
    )
    : [];
  const visibleTarget = matchingTargets.length === 1 ? matchingTargets[0] : null;
  if (toolName === "phone_scroll" && visibleTarget) {
    throw toolError(
      "EXPLICIT_TARGET_ALREADY_VISIBLE",
      "Jawny cel jest już widoczny. Kliknij „" + visibleEvidence.get(visibleTarget).value + "” zamiast przewijać.",
    );
  }
  if (toolName === "phone_click") {
    const selector = normalizeSelector(args[1]).toLowerCase();
    if (explicitTarget && onSettingsRoot) {
      if (!visibleTarget && currentScreen?.scrollable === true) {
        throw toolError(
          "EXPLICIT_TARGET_NOT_VISIBLE",
          "Jawny cel „" + (process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET || "")
            + "” nie jest widoczny. Wywołaj phone_scroll z direction=forward.",
        );
      }
      if (visibleTarget && selector !== visibleTarget) {
        throw toolError(
          "EXPLICIT_TARGET_MISMATCH",
          "Kliknij widoczny jawny cel „" + visibleEvidence.get(visibleTarget).value + "”.",
        );
      }
    }
    const visible = visibleEvidence.get(selector);
    if (!visible) {
      failedCalls.set(signature, screenFingerprint);
      recoveryObservationRequired = true;
      throw toolError("SELECTOR_NOT_VISIBLE", "Kliknięcie wymaga dokładnego tekstu, opisu lub podpowiedzi z ostatniego odczytu ekranu.");
    }
    if (visible.count > 1) {
      failedCalls.set(signature, screenFingerprint);
      recoveryObservationRequired = true;
      throw toolError("AMBIGUOUS_SELECTOR", "Na ekranie jest więcej niż jeden element o tej etykiecie. Odczytaj ekran i wybierz jednoznaczny tekst lub opis.");
    }
  }
  if (toolName !== "phone_observe" && noProgressCalls.get(signature) === screenFingerprint) {
    return {
      content: [{ type: "text", text: JSON.stringify({ ok: false, state: "REPEATED_ACTION_WITHOUT_PROGRESS", detail: "Zatrzymano ponowną akcję, ponieważ poprzednio nie zmieniła ekranu. Użyj bieżącego odczytu jako wyniku albo wybierz inną akcję." }) }],
      terminate: true,
    };
  }
  if (toolName === "phone_click" && successfulTransitions.has(transitionKey)) {
    return {
      content: [{ type: "text", text: JSON.stringify({ ok: false, state: "REPEATED_SCREEN_TRANSITION", detail: "Zatrzymano przejście, które było już wykonane z tego samego ekranu. Użyj bieżącego odczytu albo zakończ zadanie." }) }],
      terminate: true,
    };
  }
  try {
    const previousFingerprint = screenFingerprint;
    const raw = await run(args, signal);
    persistRawResult(toolName, raw);
    let parsed;
    try { parsed = JSON.parse(raw); }
    catch { throw toolError("INVALID_PHONE_RESPONSE", "Telefon zwrócił niepoprawny JSON."); }
    if (!parsed || parsed.ok !== true) {
      throw toolError(typeof parsed?.state === "string" ? parsed.state : "PHONE_ACTION_FAILED", "Akcja telefonu nie została wykonana.");
    }
    const nextFingerprint = fingerprint(parsed);
    updateVisibleEvidence(parsed);
    if (nextFingerprint !== null) {
      screenFingerprint = nextFingerprint;
      if (toolName !== "phone_observe" && nextFingerprint === previousFingerprint) {
        noProgressCalls.set(signature, nextFingerprint);
      } else {
        noProgressCalls.delete(signature);
      }
    }
    if (toolName === "phone_click" && previousFingerprint !== "unknown") successfulTransitions.add(transitionKey);
    failedCalls.delete(signature);
    const compact = JSON.parse(compactResult(parsed));
    if (toolName === "phone_observe") recoveryObservationRequired = false;
    const perception = compact.observation?.perception;
    compact.guidance = perception?.supportRecommended === true
      ? "Drzewo Accessibility nie opisuje wiarygodnie całego ekranu. Nie zgaduj brakujących elementów ani współrzędnych; wywołaj phone_request_perception_support, aby zażądać OCR lub analizy obrazu."
      : toolName === "phone_status"
      ? "Następny krok: wywołaj phone_observe."
      : nextFingerprint !== null && nextFingerprint === previousFingerprint
        ? "Ekran nie zmienił się. Nie powtarzaj tej akcji; jeśli potwierdza cel, wywołaj phone_finish, w przeciwnym razie wybierz inną widoczną akcję."
        : "Jeśli ten ekran potwierdza cel i zawiera żądane informacje, natychmiast wywołaj phone_finish z dokładnym evidence. Nie klikaj następnej pozycji. Jeśli cel nie jest jeszcze widoczny, wybierz jedną widoczną etykietę nawigacji.";
    return { content: [{ type: "text", text: JSON.stringify(compact) }] };
  } catch (error) {
    failedCalls.set(signature, screenFingerprint);
    recoveryObservationRequired = true;
    throw error;
  }
};

export default function (pi) {
  if (process.env.MOBILE_BOT_DISABLE_PHONE_TOOLS === "1") return;
  const register = (name, label, description, parameters, command) => pi.registerTool({
    name, label, description, parameters,
    execute: (_toolCallId, params, signal) => executePhone(name, command(params), signal),
  });

  register("phone_status", "Stan telefonu", "Sprawdź, czy sterowanie telefonem jest gotowe.", empty, () => ["status"]);
  register("phone_observe", "Odczytaj ekran", "Odczytaj aktualny pakiet, tytuł i widoczne elementy ekranu.", empty, () => ["observe"]);
  register("phone_list_apps", "Lista aplikacji", "Odczytaj zainstalowane aplikacje. Opcjonalnie ogranicz wynik tekstowym filtrem.", Type.Object({ filter: Type.Optional(Type.String()) }, { additionalProperties: false }), (p) => p.filter ? ["list-apps", p.filter] : ["list-apps"]);
  register("phone_home", "Ekran główny", "Przejdź na ekran główny Androida.", empty, () => ["home"]);
  register("phone_back", "Cofnij", "Cofnij o jeden ekran.", empty, () => ["back"]);
  register("phone_open_app", "Otwórz aplikację", "Otwórz aplikację za pomocą identyfikatora packageName odczytanego przez phone_list_apps.", Type.Object({ packageName: string("Pełny identyfikator pakietu aplikacji") }, { additionalProperties: false }), (p) => ["open-app", p.packageName]);
  register("phone_open_url", "Otwórz adres WWW", "Otwórz wyłącznie pełny adres WWW zaczynający się od http:// lub https://. Nie używaj do ekranów Ustawień.", Type.Object({ url: string("Pełny adres http:// lub https://") }, { additionalProperties: false }), (p) => ["open-url", p.url]);
  register("phone_open_mail", "Otwórz pocztę", "Otwórz domyślną aplikację pocztową.", empty, () => ["open-mail"]);
  register("phone_open_sms", "Otwórz SMS", "Otwórz domyślną aplikację SMS.", empty, () => ["open-sms"]);
  register("phone_click", "Kliknij", "Kliknij pierwszy widoczny element wskazany dokładnym tekstem lub opisem z ostatniego odczytu ekranu. Przekaż sam tekst, bez prefiksu text:.", Type.Object({ selector: string("Dokładny tekst lub opis elementu, bez prefiksu") }, { additionalProperties: false }), (p) => ["click", normalizeSelector(p.selector), "0"]);
  if (process.env.MOBILE_BOT_PHONE_READ_ONLY !== "1") {
    register("phone_set_text", "Wpisz tekst", "Wpisz tekst do wskazanego pola. Używaj tylko gdy polecenie pozwala zmienić zawartość pola.", Type.Object({ selector: string("Tekst, opis lub identyfikator pola"), text: Type.String({ description: "Tekst do wpisania; może być pusty przy czyszczeniu pola" }), index: Type.Optional(Type.Integer({ minimum: 0, maximum: 50 })) }, { additionalProperties: false }), (p) => ["set-text", p.selector, p.text, String(p.index || 0)]);
  }
  register("phone_scroll", "Przewiń", "Przewiń aktualny ekran do przodu lub do tyłu.", Type.Object({ direction: Type.Union([Type.Literal("forward"), Type.Literal("backward")]) }, { additionalProperties: false }), (p) => ["scroll", p.direction]);
  pi.registerTool({
    name: "phone_request_perception_support",
    label: "Poproś o wsparcie odczytu ekranu",
    description: "Zakończ etap semantyczny, gdy perception.supportRecommended=true i cel zależy od treści niewidocznej w nodes. Wskazuje potrzebę OCR, a następnie analizy obrazu.",
    parameters: empty,
    async execute() {
      if (!currentScreen) throw toolError("MISSING_OBSERVATION", "Najpierw odczytaj ekran przez phone_observe.");
      if (currentScreen.perception?.supportRecommended !== true) {
        throw toolError("ACCESSIBILITY_IS_SUFFICIENT", "Bieżący odczyt nie zaleca wsparcia inną metodą.");
      }
      return {
        content: [{ type: "text", text: JSON.stringify({
          ok: true,
          state: "PERCEPTION_SUPPORT_REQUIRED",
          summary: "Nie mogę wiarygodnie odczytać tej części ekranu z Accessibility. Potrzebne jest wsparcie OCR, a jeśli ono nie wystarczy — analiza obrazu.",
          recommendedSupport: currentScreen.perception.recommendedSupport || "ocr_then_vision",
          reasons: Array.isArray(currentScreen.perception.reasons) ? currentScreen.perception.reasons : [],
          packageName: currentScreen.packageName,
          windowTitle: currentScreen.windowTitle,
        }) }],
        terminate: true,
      };
    },
  });
  pi.registerTool({
    name: "phone_finish",
    label: "Zakończ zadanie telefonu",
    description: "Zakończ dopiero wtedy, gdy bieżący ekran potwierdza cel. Evidence musi zawierać 1–4 dokładne etykiety lub wartości z ostatniego odczytu.",
    parameters: Type.Object({
      evidence: Type.Array(Type.String({ minLength: 1, maxLength: 160, description: "Dokładna etykieta lub wartość z bieżącego ekranu" }), { minItems: 1, maxItems: 4 }),
    }, { additionalProperties: false }),
    async execute(_toolCallId, params) {
      if (!currentScreen) throw toolError("MISSING_OBSERVATION", "Najpierw odczytaj ekran przez phone_observe.");
      if (recoveryObservationRequired) {
        throw toolError("RECOVERY_OBSERVE_REQUIRED", "Poprzednia akcja nie powiodła się. Najpierw ponownie odczytaj ekran przez phone_observe.");
      }
      const explicitTarget = normalizeSelector(process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET || "").toLowerCase();
      const currentTitle = normalizeSelector(currentScreen.windowTitle || "").toLowerCase();
      const explicitTargetOpen = currentScreen.packageName === "com.android.settings"
        && (currentTitle === explicitTarget || currentTitle.startsWith(explicitTarget + " "));
      if (explicitTarget && !explicitTargetOpen) {
        const visibleTarget = currentScreen.packageName === "com.android.settings"
          && currentTitle === "settings"
          ? [...visibleEvidence.keys()].find(
            (value) => value === explicitTarget || value.startsWith(explicitTarget + " "),
          )
          : null;
        throw toolError(
          "EXPLICIT_TARGET_NOT_OPEN",
          visibleTarget
            ? "Jawny cel nie jest jeszcze otwarty. Kliknij „" + visibleEvidence.get(visibleTarget).value + "”."
            : "Jawny cel „" + (process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET || "")
              + "” nie jest jeszcze otwarty. Odczytaj ekran i przejdź do właściwej sekcji.",
        );
      }
      const canonical = [];
      const canonicalKeys = new Set();
      for (const item of params.evidence) {
        const match = visibleEvidence.get(String(item).trim().toLowerCase());
        if (!match) continue;
        const key = match.value.toLowerCase();
        if (!canonicalKeys.has(key)) {
          canonical.push(match.value);
          canonicalKeys.add(key);
        }
      }
      const ignoredCandidates = new Set(["navigate up", "navigate back", "back", "search settings"]);
      for (const value of currentEvidenceCandidates) {
        const key = value.toLowerCase();
        if (canonical.length >= 3) break;
        if (canonicalKeys.has(key) || ignoredCandidates.has(key) || key === String(currentScreen.windowTitle).toLowerCase()) continue;
        canonical.push(value);
        canonicalKeys.add(key);
      }
      if (canonical.length === 0) throw toolError("EVIDENCE_NOT_VISIBLE", "Ostatni odczyt nie zawiera tekstowego dowodu do bezpiecznego podsumowania.");
      const summary = "Odczyt z telefonu: " + canonical.join("; ") + ".";
      return {
        content: [{ type: "text", text: JSON.stringify({ ok: true, state: "GOAL_REPORTED", summary, evidence: canonical, ...currentScreen }) }],
        terminate: true,
      };
    },
  });

  if (process.env.MOBILE_BOT_ENABLE_LEGACY_PHONE_ACTION === "1") {
    const allowed = new Set(["status", "observe", "list-apps", "home", "open-app", "open-url", "open-mail", "open-sms", "click", "set-text", "scroll", "back"]);
    pi.registerTool({
      name: "phone_action",
      label: "Telefon — zgodność starej sesji",
      description: "Historyczny kontrakt telefonu dostępny tylko przy wznawianiu wcześniejszej sesji.",
      parameters: Type.Object({
        action: Type.String(), value: Type.Optional(Type.String()), selector: Type.Optional(Type.String()),
        direction: Type.Optional(Type.String()), index: Type.Optional(Type.Integer({ minimum: 0, maximum: 50 })),
      }),
      async execute(_toolCallId, params, signal) {
        if (!allowed.has(params.action)) throw toolError("INVALID_ACTION", "Nieznana akcja telefonu.");
        const needsValue = new Set(["open-app", "open-url", "click"]);
        if (needsValue.has(params.action) && !params.value) throw toolError("MISSING_VALUE", "Ta akcja wymaga value.");
        if (params.action === "set-text" && (typeof params.selector !== "string" || typeof params.value !== "string")) throw toolError("MISSING_VALUE", "set-text wymaga selector i value.");
        const args = [params.action];
        if (params.action === "set-text") args.push(params.selector, params.value, String(params.index || 0));
        else if (["click", "open-app", "open-url"].includes(params.action)) args.push(params.value, ...(params.action === "click" ? [String(params.index || 0)] : []));
        else if (params.action === "list-apps" && params.value) args.push(params.value);
        else if (params.action === "scroll") args.push(params.direction === "backward" ? "backward" : "forward");
        return executePhone("phone_action", args, signal);
      },
    });
  }
}
`;
        writeFileSync(this.extensionPath, source, { mode: 0o700 });
    }
}
export { MODEL_ID, LOCAL_AI_PI, explicitSettingsTarget };
//# sourceMappingURL=piRuntime.js.map