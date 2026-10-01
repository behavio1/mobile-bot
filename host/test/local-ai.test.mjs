import assert from "node:assert/strict";
import test from "node:test";
import { once } from "node:events";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { createHostServer, MemoryWorkspaceStore } from "../dist/index.js";
import { PiLocalRuntime, explicitSettingsTarget } from "../dist/piRuntime.js";

function fakeLocalManager() {
  let state = "stopped";
  return {
    status() {
      return {
        status: state,
        modelId: "qwen3.5-0.8b-q4_k_m",
        modelFile: state === "ready" ? "/tmp/model.gguf" : null,
        modelPath: state === "ready" ? "/tmp/model.gguf" : null,
        bytesDownloaded: state === "ready" ? 532517120 : 0,
        totalBytes: 532517120,
        operationId: null,
        errorCode: null,
        engineVersion: "b10516",
        piVersion: "0.85.1",
        updatedAt: new Date().toISOString(),
        engineReady: true,
        piReady: true,
        serverPort: 8769,
      };
    },
    async prepare() { state = "ready"; return this.status(); },
    async start() { state = "ready"; return this.status(); },
    stop() { state = "stopped"; },
  };
}

test("local runtime selection is persisted and a local run uses the Pi adapter", async (context) => {
  const local = fakeLocalManager();
  let received;
  const server = createHostServer(
    () => ({ installed: false, version: null, authenticated: false }),
    async () => assert.fail("Codex executor must not run"),
    new MemoryWorkspaceStore(),
    {
      runtimeInstallRoot: "/tmp/mobile-bot-local-runtime-test",
      localAiManager: local,
      localRunExecutor: async (input) => {
        received = input;
        return { threadId: "/tmp/mobile-bot-local-runtime-test/session.jsonl", reply: "Lokalna odpowiedź." };
      },
      conversationStarterGenerator: async (agents) => ({
        startersByAgentId: Object.fromEntries(agents.map((agent) => [agent.id, ["jeden", "dwa", "trzy", "cztery"]])),
        toolEventCount: 0,
      }),
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => server.close());
  const address = server.address();
  const base = `http://127.0.0.1:${address.port}`;

  const selected = await fetch(`${base}/runtime`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ selected: "pi-local" }),
  });
  assert.equal(selected.status, 200);
  assert.equal((await selected.json()).selected, "pi-local");

  const prepared = await fetch(`${base}/runtime/local/prepare`, { method: "POST" });
  assert.equal(prepared.status, 202);

  const run = await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "starter-atlas", prompt: "Odpowiedz lokalnie" }),
  });
  assert.equal(run.status, 200);
  assert.equal((await run.json()).reply, "Lokalna odpowiedź.");
  assert.equal(received.agent.id, "starter-atlas");

  const health = await (await fetch(`${base}/health`)).json();
  assert.equal(health.runtime.selected, "pi-local");
  assert.equal(health.codex.authenticated, false);
});

test("Pi phone extension exposes typed phone tools and marks phone failures as errors", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-pi-tools-"));
  try {
    new PiLocalRuntime({ port: 8769 }, root);
    const extension = readFileSync(join(root, "local-ai", "phone-tools.mjs"), "utf8");
    for (const name of [
      "phone_status", "phone_observe", "phone_list_apps", "phone_home", "phone_back",
      "phone_open_app", "phone_open_url", "phone_open_mail", "phone_open_sms",
      "phone_click", "phone_set_text", "phone_scroll",
    ]) assert.match(extension, new RegExp(`register\\(\\"${name}\\"`));
    assert.match(extension, /name: "phone_request_perception_support"/);
    assert.match(extension, /name: "phone_finish"/);
    assert.match(extension, /packageName: string\(/);
    assert.match(extension, /selector: string\(/);
    assert.match(extension, /parsed\.ok !== true/);
    assert.match(extension, /state: \"REPEATED_FAILED_ACTION\"/);
    assert.match(extension, /state: \"REPEATED_ACTION_WITHOUT_PROGRESS\"/);
    assert.match(extension, /state: \"REPEATED_SCREEN_TRANSITION\"/);
    assert.match(extension, /RECOVERY_OBSERVE_REQUIRED/);
    assert.match(extension, /EXPLICIT_TARGET_NOT_OPEN/);
    assert.match(extension, /state: \"GOAL_REPORTED\"/);
    assert.match(extension, /maxLength: 160/);
    assert.doesNotMatch(extension, /summary: string\(/);
    assert.match(extension, /MOBILE_BOT_PHONE_READ_ONLY !== \"1\"/);
    assert.match(extension, /MOBILE_BOT_DISABLE_PHONE_TOOLS === \"1\"/);
    assert.match(extension, /terminate: true/);
    assert.match(extension, /Type\.Object\(\{ selector: string\(\"Dokładny tekst lub opis elementu, bez prefiksu\"\) \}/);
    assert.match(extension, /normalizeSelector\(p\.selector\)/);
    assert.match(extension, /MOBILE_BOT_ENABLE_LEGACY_PHONE_ACTION/);
    assert.match(extension, /compactResult\(parsed\)/);
    assert.doesNotMatch(extension, /raw\.slice\(/);
    const settings = JSON.parse(readFileSync(join(root, "local-ai", "pi-home", "settings.json"), "utf8"));
    assert.deepEqual(settings.compaction, { enabled: true, reserveTokens: 2048, keepRecentTokens: 4096 });
    const models = JSON.parse(readFileSync(join(root, "local-ai", "pi-home", "models.json"), "utf8"));
    const model = models.providers["mobile-bot-local"].models[0];
    assert.equal(model.contextWindow, 16384);
    assert.equal(model.maxTokens, 1024);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("Pi extracts only one explicit parenthesized Settings target", () => {
  assert.equal(
    explicitSettingsTarget("Otwórz Pamięć (Storage) w Ustawieniach."),
    "Storage",
  );
  assert.equal(
    explicitSettingsTarget("Otwórz w Ustawieniach Połączone urządzenia (Connected devices)."),
    "Connected devices",
  );
  assert.equal(explicitSettingsTarget("Otwórz (Storage) albo (Battery) w Ustawieniach."), null);
  assert.equal(explicitSettingsTarget("Wyjaśnij pojęcie (Storage)."), null);
});

test("Pi phone extension stops an identical successful action that made no screen progress", async () => {
  const root = mkdtempSync(join(resolve("."), ".tmp-mobile-bot-pi-tools-"));
  const priorClient = process.env.MOBILE_BOT_PHONE_CLIENT;
  const priorLog = process.env.MOBILE_BOT_LOCAL_TOOL_LOG;
  const priorExplicitTarget = process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET;
  try {
    new PiLocalRuntime({ port: 8769 }, root);
    const calls = join(root, "calls.ndjson");
    const client = join(root, "fake-phone-client.mjs");
    writeFileSync(client, `
import { appendFileSync } from "node:fs";
appendFileSync(${JSON.stringify(calls)}, JSON.stringify(process.argv.slice(2)) + "\\n");
console.log(JSON.stringify({
  ok: true,
  state: "ACTION_PERFORMED",
  matchCount: 1,
  observation: {
    ok: true,
    state: "READY",
    packageName: "com.android.settings",
    windowTitle: "Settings",
    readableNodeCount: 1,
    ...(process.env.FAKE_PHONE_NEEDS_SUPPORT === "1" ? {
      perception: {
        source: "accessibility",
        quality: "insufficient",
        supportRecommended: true,
        recommendedSupport: "ocr_then_vision",
        reasons: ["no_semantic_content"]
      }
    } : {}),
    nodes: [
      { viewId: "com.android.settings:id/main_content_scrollable_container", scrollable: true },
      { text: "Search settings", clickable: true, bounds: "0,0,100,100" },
      { text: "Pair new device", bounds: "0,200,100,300" },
      { text: "Saved devices", bounds: "0,300,100,400" },
      ...(process.env.FAKE_DUPLICATE === "1" ? [{ text: "Search settings", bounds: "0,100,100,200" }] : [])
    ]
  }
}));
`);
    process.env.MOBILE_BOT_PHONE_CLIENT = client;
    process.env.MOBILE_BOT_LOCAL_TOOL_LOG = join(root, "tool-results.ndjson");
    const typebox = join(root, "node_modules", "typebox");
    mkdirSync(typebox, { recursive: true });
    writeFileSync(join(typebox, "package.json"), JSON.stringify({ type: "module", exports: "./index.js" }));
    writeFileSync(join(typebox, "index.js"), `
const schema = (type, options = {}) => ({ type, ...options });
export const Type = {
  Object: (properties, options = {}) => ({ type: "object", properties, ...options }),
  String: (options = {}) => schema("string", options),
  Optional: (value) => value,
  Integer: (options = {}) => schema("integer", options),
  Array: (items, options = {}) => ({ type: "array", items, ...options }),
  Union: (values) => ({ anyOf: values }),
  Literal: (value) => ({ const: value })
};
`);
    const extensionPath = join(root, "local-ai", "phone-tools.mjs");
    const extension = await import(`${pathToFileURL(extensionPath).href}?test=${Date.now()}`);
    const tools = new Map();
    extension.default({ registerTool(tool) { tools.set(tool.name, tool); } });
    await tools.get("phone_observe").execute("observe-1", {});
    process.env.FAKE_PHONE_NEEDS_SUPPORT = "1";
    const needsSupport = await tools.get("phone_observe").execute("observe-needs-support", {});
    assert.match(needsSupport.content[0].text, /\"quality\":\"insufficient\"/);
    assert.match(needsSupport.content[0].text, /Nie zgaduj brakujących elementów/);
    const support = await tools.get("phone_request_perception_support").execute("support-1", {});
    assert.equal(support.terminate, true);
    assert.match(support.content[0].text, /PERCEPTION_SUPPORT_REQUIRED/);
    assert.match(support.content[0].text, /ocr_then_vision/);
    delete process.env.FAKE_PHONE_NEEDS_SUPPORT;
    const grounded = await tools.get("phone_finish").execute("finish-grounded", { evidence: ["Nie ma mnie"] });
    assert.match(grounded.content[0].text, /Odczyt z telefonu: Pair new device; Saved devices\./);
    const finish = await tools.get("phone_finish").execute("finish-valid", { evidence: ["Search settings", "Search settings"] });
    assert.equal(finish.terminate, true);
    assert.match(finish.content[0].text, /GOAL_REPORTED/);
    assert.match(finish.content[0].text, /Odczyt z telefonu: Search settings; Pair new device; Saved devices\./);
    const click = tools.get("phone_click");
    process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET = "Storage";
    await assert.rejects(
      click.execute("wrong-explicit-target", { selector: "Pair new device" }),
      /EXPLICIT_TARGET_NOT_VISIBLE/,
    );
    process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET = "Pair new device";
    await assert.rejects(
      click.execute("visible-explicit-target-mismatch", { selector: "Search settings" }),
      /EXPLICIT_TARGET_MISMATCH/,
    );
    process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET = "Pair";
    await assert.rejects(
      tools.get("phone_finish").execute("finish-before-explicit-target", { evidence: ["Pair new device"] }),
      /EXPLICIT_TARGET_NOT_OPEN/,
    );
    await assert.rejects(
      tools.get("phone_scroll").execute("visible-target-scroll", { direction: "forward" }),
      /EXPLICIT_TARGET_ALREADY_VISIBLE/,
    );
    const expandedTarget = await click.execute(
      "visible-expanded-explicit-target",
      { selector: "Pair new device" },
    );
    assert.equal(expandedTarget.terminate, undefined);
    delete process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET;
    const first = await click.execute("call-1", { selector: "Search settings" });
    assert.equal(first.terminate, undefined);
    assert.match(first.content[0].text, /Ekran nie zmienił się/);
    const second = await click.execute("call-2", { selector: "Search settings" });
    assert.equal(second.terminate, true);
    assert.match(second.content[0].text, /REPEATED_ACTION_WITHOUT_PROGRESS/);
    assert.equal(readFileSync(calls, "utf8").trim().split("\n").length, 4);

    process.env.FAKE_DUPLICATE = "1";
    await tools.get("phone_observe").execute("observe-duplicate-1", {});
    await assert.rejects(
      click.execute("ambiguous-1", { selector: "Search settings" }),
      /AMBIGUOUS_SELECTOR/,
    );
    await assert.rejects(
      tools.get("phone_finish").execute("finish-after-failure", { evidence: ["Pair new device"] }),
      /RECOVERY_OBSERVE_REQUIRED/,
    );
    await tools.get("phone_observe").execute("observe-duplicate-2", {});
    const recoveredFinish = await tools.get("phone_finish").execute(
      "finish-after-recovery-observe",
      { evidence: ["Pair new device"] },
    );
    assert.match(recoveredFinish.content[0].text, /GOAL_REPORTED/);
    const repeatedAmbiguous = await click.execute("ambiguous-2", { selector: "Search settings" });
    assert.equal(repeatedAmbiguous.terminate, true);
    assert.match(repeatedAmbiguous.content[0].text, /REPEATED_FAILED_ACTION/);
    assert.equal(readFileSync(calls, "utf8").trim().split("\n").length, 6);
  } finally {
    delete process.env.FAKE_DUPLICATE;
    delete process.env.FAKE_PHONE_NEEDS_SUPPORT;
    if (priorClient === undefined) delete process.env.MOBILE_BOT_PHONE_CLIENT;
    else process.env.MOBILE_BOT_PHONE_CLIENT = priorClient;
    if (priorLog === undefined) delete process.env.MOBILE_BOT_LOCAL_TOOL_LOG;
    else process.env.MOBILE_BOT_LOCAL_TOOL_LOG = priorLog;
    if (priorExplicitTarget === undefined) delete process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET;
    else process.env.MOBILE_BOT_EXPLICIT_SETTINGS_TARGET = priorExplicitTarget;
    rmSync(root, { recursive: true, force: true });
  }
});

test("Pi runtime closes RPC stdin and has bounded TERM/KILL cleanup", () => {
  const source = readFileSync(new URL("../src/piRuntime.ts", import.meta.url), "utf8");
  assert.match(source, /child\.stdin\.end\(\)/);
  assert.match(source, /requestsReadOnlyPhoneUse\(input\.prompt\)/);
  assert.match(source, /prohibitsPhoneTools\(input\.prompt\)/);
  assert.match(source, /cleanModelReply\(text\)/);
  assert.match(source, /event\.type === "tool_execution_end"/);
  assert.match(source, /if \(!reply\.trim\(\) && lastToolFailure\) reply = lastToolFailure/);
  assert.match(source, /if \(terminalToolReply\.trim\(\)\) reply = terminalToolReply/);
  assert.match(source, /child\.kill\("SIGTERM"\)/);
  assert.match(source, /child\.kill\("SIGKILL"\)/);
  assert.match(source, /if \(buffer\.trim\(\)\) consume\(buffer\)/);
});

test("local llama server uses the verified context and deterministic sampling", () => {
  const source = readFileSync(new URL("../src/localAi.ts", import.meta.url), "utf8");
  assert.match(source, /"--ctx-size", String\(LOCAL_AI_CONTEXT_WINDOW\)/);
  assert.match(source, /"--temp", "0"/);
  assert.match(source, /enable_thinking\":false/);
  assert.match(source, /this\.server\.kill\("SIGKILL"\)/);
  assert.match(source, /await this\.probeHealth\(\)/);
  assert.match(source, /this\.server\.exitCode === null/);
});

test("Pi system prompt requires observe, one action and verified readback", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-pi-prompt-"));
  try {
    const runtime = new PiLocalRuntime({ port: 8769 }, root);
    const prompt = runtime.systemPrompt({
      agent: { id: "atlas", name: "Atlas", systemPrompt: "Rola testowa", ownerProfile: "", skills: [] },
      prompt: "Odczytaj ekran telefonu. Tylko odczyt.",
    });
    assert.match(prompt, /phone_status/);
    assert.match(prompt, /phone_observe/);
    assert.match(prompt, /phone_request_perception_support/);
    assert.match(prompt, /dokładną etykietę celu/);
    assert.match(prompt, /celu nie ma w `nodes`/);
    assert.match(prompt, /phone_finish/);
    assert.match(prompt, /Tylko odczyt/);
    assert.ok(prompt.length < 800);
    const generalPrompt = runtime.systemPrompt({
      agent: { id: "atlas", name: "Atlas", systemPrompt: "Rola testowa", ownerProfile: "", skills: [] },
      prompt: "Porównaj dwa dokumenty.",
    });
    assert.match(generalPrompt, /Rola testowa/);
    assert.match(generalPrompt, /pełnych adresów http:\/\/ lub https:\/\//);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
