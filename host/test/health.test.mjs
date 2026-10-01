import assert from "node:assert/strict";
import test from "node:test";
import { once } from "node:events";
import { mkdtempSync, readFileSync, rmSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  createHostServer,
  conversationStarterCodexArgs,
  conversationStarterCodexEnvironment,
  DevelopmentActionLogger,
  HOST_DEVELOPMENT_ACTIONS,
  HOST_EVENT_REQUIRED_METADATA,
  materializeAgentWorkspace,
  MemoryWorkspaceStore,
  parseConversationStarterCodexOutput,
  renderAgentInstructions,
  SqliteWorkspaceStore,
} from "../dist/index.js";

function fakeConversationStarterGenerator(agents) {
  return Promise.resolve({
    startersByAgentId: Object.fromEntries(agents.map((agent) => [
      agent.id,
      [1, 2, 3, 4].map((index) => `${agent.name}: propozycja ${index}`),
    ])),
    toolEventCount: 0,
  });
}

function metadataFor(action, overrides = {}) {
  return {
    ...Object.fromEntries(HOST_EVENT_REQUIRED_METADATA[action].map((key) => [key, null])),
    ...overrides,
  };
}

test("development logger separates parallel traces, pairs lifecycle events and redacts payload fields", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-action-log-"));
  const logPath = join(root, "actions.jsonl");
  try {
    const logger = new DevelopmentActionLogger(
      true,
      logPath,
      () => new Date("2026-09-07T21:15:00.000Z"),
    );
    const first = { traceId: "trace-agent-a", actionId: "action-agent-a" };
    const second = { traceId: "trace-agent-b", actionId: "action-agent-b" };
    const firstStartedMetadata = metadataFor("codex.run_started", {
      agentId: "agent-a",
      agentName: "Atlas",
      promptLength: 19,
    });
    logger.record("codex.run_started", firstStartedMetadata, first);
    logger.record("codex.run_started", metadataFor("codex.run_started", {
      agentId: "agent-b", agentName: "Nova",
    }), second);
    logger.record("codex.run_completed", metadataFor("codex.run_completed", {
      agentId: "agent-a", agentName: "Atlas",
    }), first);
    logger.record("codex.run_started", {
      ...firstStartedMetadata,
      prompt: "must-not-be-written",
      ownerProfile: "also-must-not-be-written",
    }, first);

    const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
    assert.equal(events.length, 4);
    assert.equal(new Set(events.map((event) => event.eventId)).size, 4);
    assert.deepEqual(events.filter((event) => event.traceId === first.traceId).map((event) => event.action), [
      "codex.run_started", "codex.run_completed", "codex.run_started",
    ]);
    assert.ok(events.filter((event) => event.traceId === first.traceId).every(
      (event) => event.actionId === first.actionId && event.metadata.agentId === "agent-a",
    ));
    assert.equal(events[0].timestamp, "2026-09-07T21:15:00.000Z");
    assert.equal(events[0].metadata.promptLength, 19);
    assert.equal(events[3].metadata.prompt, "[redacted]");
    assert.equal(events[3].metadata.ownerProfile, "[redacted]");
    assert.deepEqual(events.map((event) => event.sequence), [1, 2, 3, 4]);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("every declared Host event propagates identity and correlation fields", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-all-action-log-"));
  const logPath = join(root, "actions.jsonl");
  try {
    const logger = new DevelopmentActionLogger(
      true,
      logPath,
      () => new Date("2026-09-07T21:16:00.000Z"),
    );
    const correlation = {
      traceId: "trace-all-host-events",
      actionId: "action-all-host-events",
      parentTraceId: "trace-parent",
      parentActionId: "action-parent",
    };
    const actions = Object.values(HOST_DEVELOPMENT_ACTIONS);
    assert.deepEqual(new Set(Object.keys(HOST_EVENT_REQUIRED_METADATA)), new Set(actions));
    actions.forEach((action) => {
      const required = new Set(HOST_EVENT_REQUIRED_METADATA[action]);
      const metadata = metadataFor(action, {
        ...(required.has("agentId") ? { agentId: "agent-test" } : {}),
        ...(required.has("agentName") ? { agentName: "Test" } : {}),
      });
      logger.record(action, metadata, correlation);
      const firstRequired = HOST_EVENT_REQUIRED_METADATA[action][0];
      assert.throws(
        () => logger.record(action, Object.fromEntries(
          Object.entries(metadata).filter(([key]) => key !== firstRequired),
        ), correlation),
        new RegExp(`development_action_missing_metadata:${action}`),
      );
    });

    const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
    assert.equal(events.length, actions.length);
    assert.deepEqual(new Set(events.map((event) => event.action)), new Set(actions));
    assert.equal(new Set(events.map((event) => event.eventId)).size, events.length);
    assert.ok(events.every((event) => event.traceId === correlation.traceId));
    assert.ok(events.every((event) => event.actionId === correlation.actionId));
    assert.ok(events.every((event) => event.parentTraceId === correlation.parentTraceId));
    assert.ok(events.every((event) => event.parentActionId === correlation.parentActionId));
    assert.ok(events.filter((event) => Object.hasOwn(event.metadata, "agentId")).every(
      (event) => event.metadata.agentId === "agent-test",
    ));
    assert.deepEqual(events.map((event) => event.sequence), actions.map((_, index) => index + 1));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

async function withServer(context, runExecutor, options = {}, store = new MemoryWorkspaceStore()) {
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    runExecutor,
    store,
    { conversationStarterGenerator: fakeConversationStarterGenerator, ...options },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => server.close());
  const address = server.address();
  assert.ok(address && typeof address === "object");
  return `http://127.0.0.1:${address.port}`;
}

test("health exposes compatible versions without account output", async (context) => {
  const base = await withServer(context, async () => assert.fail("run should not start"));
  const response = await fetch(`${base}/health`);
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.protocolVersion, 5);
  assert.equal(body.environmentRevision, 56);
  assert.equal(body.hostVersion, "0.5.50");
  assert.equal(body.codex.installed, true);
  assert.equal(body.codex.authenticated, true);
  assert.equal(JSON.stringify(body).includes("email"), false);
});

test("unknown routes fail closed", async (context) => {
  const base = await withServer(context, async () => assert.fail("run should not start"));
  const response = await fetch(`${base}/unknown`, { method: "POST" });
  assert.equal(response.status, 404);
});

test("run passes dedicated agent context to Codex and persists real conversation", async (context) => {
  let received;
  const base = await withServer(context, async (input) => {
    received = input;
    return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc325c", reply: "Połączenie z Atlasem działa." };
  });

  const profileResponse = await fetch(`${base}/owner-profile`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ about: "Mam na imię Alicja. Projektuję produkty i interesuję się fotografią." }),
  });
  assert.equal(profileResponse.status, 200);

  const response = await fetch(`${base}/runs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-atlas-request",
    },
    body: JSON.stringify({ agentId: "starter-atlas", prompt: "Sprawdź połączenie" }),
  });
  const run = await response.json();

  assert.equal(response.status, 200);
  assert.equal(run.status, "completed");
  assert.equal(run.reply, "Połączenie z Atlasem działa.");
  assert.equal(received.agent.name, "Atlas");
  assert.match(received.agent.ownerProfile, /Alicja/);
  assert.equal(received.traceId, "trace-atlas-request");
  assert.match(received.agent.systemPrompt, /Analyze sources/);
  assert.deepEqual(received.agent.skills.map((skill) => skill.id), [
    "web-research", "source-comparison", "change-monitoring",
  ]);

  const workspace = await (await fetch(`${base}/workspace`)).json();
  assert.match(workspace.ownerProfile, /fotografią/);
  assert.equal(workspace.agents.length, 6);
  assert.equal(workspace.recentConversations.length, 1);
  assert.equal(workspace.recentCompletedTasks.length, 1);
  const history = await (await fetch(
    `${base}/conversations/${run.conversationId}/messages`,
  )).json();
  assert.deepEqual(history.messages.map((message) => message.role), ["user", "agent"]);
});

test("successful phone run returns once after persisting its terminal result", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-return-success-"));
  const logPath = join(root, "actions.jsonl");
  const store = new MemoryWorkspaceStore();
  const returns = [];
  let messagesAtReturn = [];
  let base = "";
  context.after(() => rmSync(root, { recursive: true, force: true }));
  base = await withServer(
    context,
    async (input) => {
      for (const action of ["open_sms", "click"]) {
        const event = await fetch(`${base}/v5/executor/events`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ runId: input.runId, action, ok: true, state: "READY" }),
        });
        assert.equal(event.status, 202);
      }
      return { threadId: "thread-phone-success", reply: "SMS wysłany." };
    },
    {
      actionLogger: new DevelopmentActionLogger(true, logPath),
      runTerminalReturner: async (returnContext) => {
        returns.push(returnContext);
        messagesAtReturn = store.messages(returnContext.conversationId)?.map((message) => message.role) ?? [];
      },
    },
    store,
  );

  const response = await fetch(`${base}/runs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-phone-success",
    },
    body: JSON.stringify({ agentId: "starter-nova", prompt: "Wyślij SMS" }),
  });
  const run = await response.json();

  assert.equal(response.status, 200);
  assert.equal(run.status, "completed");
  assert.deepEqual(messagesAtReturn, ["user", "agent"]);
  assert.equal(returns.length, 1);
  assert.deepEqual(returns[0], {
    traceId: "trace-phone-success",
    parentTraceId: null,
    runId: run.runId,
    agentId: "starter-nova",
    conversationId: run.conversationId,
    automationId: null,
  });
  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  assert.equal(events.filter((event) => event.action === "mobile_app.return_completed").length, 1);
  assert.ok(events.every((event) => !JSON.stringify(event).includes("Wyślij SMS")));
  assert.ok(events.every((event) => !JSON.stringify(event).includes("SMS wysłany")));
});

test("failed phone run still returns with its run context", async (context) => {
  const returns = [];
  let runInput;
  let base = "";
  base = await withServer(
    context,
    async (input) => {
      runInput = input;
      await fetch(`${base}/v5/executor/events`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ runId: input.runId, action: "open_sms", ok: false, state: "ACTION_REJECTED" }),
      });
      throw new Error("codex failed after phone action");
    },
    { runTerminalReturner: async (returnContext) => { returns.push(returnContext); } },
  );

  const response = await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "starter-nova", prompt: "Wyślij SMS" }),
  });

  assert.equal(response.status, 502);
  assert.equal((await response.json()).error, "codex_run_failed");
  assert.equal(returns.length, 1);
  assert.equal(returns[0].runId, runInput.runId);
  assert.equal(returns[0].agentId, runInput.agent.id);
  assert.equal(returns[0].conversationId, runInput.conversationId);
});

test("run without a phone event does not request an app return", async (context) => {
  let returnCount = 0;
  const base = await withServer(
    context,
    async () => ({ threadId: "thread-no-phone", reply: "Odpowiedź bez telefonu." }),
    { runTerminalReturner: async () => { returnCount += 1; } },
  );

  const response = await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "starter-atlas", prompt: "Odpowiedz lokalnie" }),
  });

  assert.equal(response.status, 200);
  assert.equal(returnCount, 0);
});

test("app return failure is logged without replacing the successful run result", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-return-failure-"));
  const logPath = join(root, "actions.jsonl");
  let base = "";
  context.after(() => rmSync(root, { recursive: true, force: true }));
  base = await withServer(
    context,
    async (input) => {
      await fetch(`${base}/v5/executor/events`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ runId: input.runId, action: "open_sms", ok: true, state: "READY" }),
      });
      return { threadId: "thread-return-failure", reply: "Zadanie zakończone." };
    },
    {
      actionLogger: new DevelopmentActionLogger(true, logPath),
      runTerminalReturner: async () => { throw new Error("mobile_app_return_target_app_timeout"); },
    },
  );

  const response = await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "starter-nova", prompt: "Wykonaj akcję telefonu" }),
  });
  const run = await response.json();

  assert.equal(response.status, 200);
  assert.equal(run.status, "completed");
  assert.equal(run.reply, "Zadanie zakończone.");
  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  const failedReturn = events.find((event) => event.action === "mobile_app.return_failed");
  assert.equal(failedReturn.metadata.runId, run.runId);
  assert.equal(failedReturn.metadata.agentId, "starter-nova");
  assert.equal(failedReturn.metadata.conversationId, run.conversationId);
  assert.equal(failedReturn.metadata.errorCode, "mobile_app_return_failed");
  assert.equal(events.some((event) => event.action === "mobile_app.return_completed"), false);
});

test("skills API exposes system definitions, persists assignments and preserves the local learned copy", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-skill-assignment-"));
  const workspaceRoot = join(root, "workspaces");
  const logPath = join(root, "actions.jsonl");
  const actionLogger = new DevelopmentActionLogger(true, logPath);
  const runs = [];
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async (input) => {
      runs.push(input);
      return { threadId: `thread-${runs.length}`, reply: "Gotowe" };
    },
    new MemoryWorkspaceStore(),
    {
      actionLogger,
      agentWorkspaceRoot: workspaceRoot,
      conversationStarterGenerator: fakeConversationStarterGenerator,
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => {
    server.close();
    rmSync(root, { recursive: true, force: true });
  });
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const base = `http://127.0.0.1:${address.port}`;

  const before = await (await fetch(`${base}/workspace`)).json();
  assert.equal(before.skillDefinitions.length, 12);
  assert.equal(before.skillDefinitions.find((skill) => skill.id === "theme-builder").assignable, true);
  assert.equal(new Set(before.skillDefinitions.map((skill) => skill.id)).size, 12);
  assert.equal(before.skillDefinitions.find((skill) => skill.id === "agent-workspace-okf").assignable, false);
  assert.deepEqual(
    before.agents.find((agent) => agent.id === "starter-atlas").assignedSkillIds,
    ["web-research", "source-comparison", "change-monitoring"],
  );

  const skillIds = ["web-research", "source-comparison", "change-monitoring", "writing"];
  const update = await fetch(`${base}/agents/starter-atlas/skills`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-assign-writing",
    },
    body: JSON.stringify({ skillIds }),
  });
  assert.equal(update.status, 200);
  assert.deepEqual((await update.json()).agent.assignedSkillIds, skillIds);
  const localSkillPath = join(workspaceRoot, "starter-atlas", ".agents", "skills", "writing", "SKILL.md");
  const initialLocalCopy = readFileSync(localSkillPath, "utf8");
  assert.match(initialLocalCopy, /<!-- mobile-bot-local-learning -->/);
  writeFileSync(localSkillPath, `${initialLocalCopy}\nVerified local learning.\n`);

  const repeated = await fetch(`${base}/agents/starter-atlas/skills`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ skillIds }),
  });
  assert.equal(repeated.status, 200);
  assert.match(readFileSync(localSkillPath, "utf8"), /Verified local learning\./);

  const firstRun = await (await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "starter-atlas", prompt: "Wymień skillsy" }),
  })).json();
  await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      agentId: "starter-atlas",
      conversationId: firstRun.conversationId,
      prompt: "Wymień skillsy ponownie",
    }),
  });
  await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "starter-atlas", prompt: "Nowa rozmowa: wymień skillsy" }),
  });
  assert.equal(runs.length, 3);
  assert.ok(runs.every((run) => run.agent.skills.map((skill) => skill.id).join(",") === skillIds.join(",")));

  const after = await (await fetch(`${base}/workspace`)).json();
  assert.deepEqual(after.agents.find((agent) => agent.id === "starter-atlas").assignedSkillIds, skillIds);
  const invalid = await fetch(`${base}/agents/starter-atlas/skills`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ skillIds: [...skillIds, "missing-definition"] }),
  });
  assert.equal(invalid.status, 400);
  assert.equal((await invalid.json()).error, "skill_definition_not_found");
  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  const assignment = events.find((event) => event.action === "agent.skills_updated");
  assert.equal(assignment.traceId, "trace-assign-writing");
  assert.deepEqual(assignment.metadata.assignedSkillIds, skillIds);
  assert.equal(assignment.metadata.assignmentCount, 4);
  assert.equal(JSON.stringify(assignment).includes("Verified local learning"), false);
});

test("SQLite keeps skill assignments when the Host store is recreated", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-skill-restart-"));
  const databasePath = join(root, "workspace.db");
  const firstServer = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async () => assert.fail("run should not start"),
    new SqliteWorkspaceStore(databasePath),
    {
      agentWorkspaceRoot: join(root, "workspaces"),
      conversationStarterGenerator: fakeConversationStarterGenerator,
    },
  );
  firstServer.listen(0, "127.0.0.1");
  await once(firstServer, "listening");
  const firstAddress = firstServer.address();
  assert.ok(firstAddress && typeof firstAddress === "object");
  const firstBase = `http://127.0.0.1:${firstAddress.port}`;
  const skillIds = ["web-research", "writing"];
  const update = await fetch(`${firstBase}/agents/starter-atlas/skills`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ skillIds }),
  });
  assert.equal(update.status, 200);
  await new Promise((resolve) => firstServer.close(resolve));

  const secondServer = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async () => assert.fail("run should not start"),
    new SqliteWorkspaceStore(databasePath),
  );
  secondServer.listen(0, "127.0.0.1");
  await once(secondServer, "listening");
  context.after(() => {
    secondServer.close();
    rmSync(root, { recursive: true, force: true });
  });
  const secondAddress = secondServer.address();
  assert.ok(secondAddress && typeof secondAddress === "object");
  const snapshot = await (await fetch(`http://127.0.0.1:${secondAddress.port}/workspace`)).json();
  assert.deepEqual(snapshot.agents.find((agent) => agent.id === "starter-atlas").assignedSkillIds, skillIds);
});

test("custom agent creation persists its id, materializes its workspace and runs the first task", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-create-agent-"));
  const workspaceRoot = join(root, "workspaces");
  const logPath = join(root, "actions.jsonl");
  const actionLogger = new DevelopmentActionLogger(true, logPath);
  let received;
  const store = new MemoryWorkspaceStore();
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async (input) => {
      received = input;
      return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc3260", reply: "Raport jest gotowy." };
    },
    store,
    {
      actionLogger,
      agentWorkspaceRoot: workspaceRoot,
      conversationStarterGenerator: fakeConversationStarterGenerator,
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => {
    server.close();
    rmSync(root, { recursive: true, force: true });
  });
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const base = `http://127.0.0.1:${address.port}`;

  const createResponse = await fetch(`${base}/agents`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-create-orion",
    },
    body: JSON.stringify({ name: "  Orion  " }),
  });
  const created = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.match(created.agent.id, /^agent-[0-9a-f-]{36}$/);
  assert.equal(created.agent.name, "Orion");
  assert.equal(created.agent.initial, "O");
  assert.match(created.agent.roleDescription, /You are the agent "Orion"/);
  assert.equal(created.agent.conversationStarters.length, 4);

  const agentWorkspace = join(workspaceRoot, created.agent.id);
  assert.equal(existsSync(agentWorkspace), true);
  const instructions = readFileSync(join(agentWorkspace, "AGENTS.md"), "utf8");
  assert.match(instructions, new RegExp(created.agent.id));
  assert.match(instructions, /Your name: Orion/);

  const createdSnapshot = await (await fetch(`${base}/workspace`)).json();
  assert.equal(createdSnapshot.agents.filter((agent) => agent.id === created.agent.id).length, 1);

  const runResponse = await fetch(`${base}/runs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-create-orion",
    },
    body: JSON.stringify({ agentId: created.agent.id, prompt: "Przygotuj raport tygodniowy" }),
  });
  const run = await runResponse.json();
  assert.equal(runResponse.status, 200);
  assert.equal(run.reply, "Raport jest gotowy.");
  assert.equal(received.agent.id, created.agent.id);
  assert.equal(received.agent.name, "Orion");
  assert.match(received.agent.systemPrompt, /You are the agent "Orion"/);

  const finalSnapshot = await (await fetch(`${base}/workspace`)).json();
  assert.equal(finalSnapshot.recentConversations[0].agentId, created.agent.id);
  assert.equal(finalSnapshot.recentCompletedTasks[0].agentId, created.agent.id);
  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  const createdEvent = events.find((event) => event.action === "agent.created");
  assert.equal(createdEvent.traceId, "trace-create-orion");
  assert.equal(createdEvent.metadata.agentId, created.agent.id);
  assert.equal(createdEvent.metadata.workspaceMaterialized, true);
  assert.ok(events.some((event) =>
    event.action === "codex.run_completed" &&
    event.traceId === "trace-create-orion" &&
    event.metadata.agentId === created.agent.id
  ));
});

test("renaming an agent preserves its id, SQLite workspace and local files", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-rename-agent-"));
  const databasePath = join(root, "workspace.db");
  const workspaceRoot = join(root, "workspaces");
  const logPath = join(root, "actions.jsonl");
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async () => assert.fail("run should not start"),
    new SqliteWorkspaceStore(databasePath),
    {
      actionLogger: new DevelopmentActionLogger(true, logPath),
      agentWorkspaceRoot: workspaceRoot,
      conversationStarterGenerator: fakeConversationStarterGenerator,
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => {
    server.close();
    rmSync(root, { recursive: true, force: true });
  });
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const base = `http://127.0.0.1:${address.port}`;

  const create = await fetch(`${base}/agents`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "Orion" }),
  });
  const created = (await create.json()).agent;
  const agentWorkspace = join(workspaceRoot, created.id);
  mkdirSync(join(agentWorkspace, "okf"), { recursive: true });
  writeFileSync(join(agentWorkspace, "okf", "index.md"), "# Trwała pamięć Oriona\n");
  writeFileSync(join(agentWorkspace, "local-result.txt"), "Nie usuwaj tego wyniku.\n");

  const rename = await fetch(`${base}/agents/${created.id}/name`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-rename-agent",
    },
    body: JSON.stringify({ name: "  Vega  " }),
  });
  const renamed = (await rename.json()).agent;
  assert.equal(rename.status, 200);
  assert.equal(renamed.id, created.id);
  assert.equal(renamed.name, "Vega");
  assert.equal(renamed.initial, "V");
  assert.deepEqual(renamed.assignedSkillIds, []);
  assert.equal(readFileSync(join(agentWorkspace, "local-result.txt"), "utf8"), "Nie usuwaj tego wyniku.\n");
  assert.equal(readFileSync(join(agentWorkspace, "okf", "index.md"), "utf8"), "# Trwała pamięć Oriona\n");
  const instructions = readFileSync(join(agentWorkspace, "AGENTS.md"), "utf8");
  assert.match(instructions, new RegExp(`Agent ID: ${created.id}`));
  assert.match(instructions, /Your name: Vega/);
  assert.match(instructions, /You are the agent "Vega"/);
  assert.doesNotMatch(instructions, /Twoje imię: Orion/);

  const persisted = new SqliteWorkspaceStore(databasePath).workspace();
  const persistedAgent = persisted.agents.find((agent) => agent.id === created.id);
  assert.equal(persistedAgent.name, "Vega");
  assert.equal(persisted.agents.filter((agent) => agent.id === created.id).length, 1);

  const invalid = await fetch(`${base}/agents/${created.id}/name`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "bad\nname" }),
  });
  assert.equal(invalid.status, 400);
  assert.equal((await invalid.json()).error, "invalid_agent_name");
  const unchanged = await (await fetch(`${base}/workspace`)).json();
  assert.equal(unchanged.agents.find((agent) => agent.id === created.id).name, "Vega");

  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  const renamedEvent = events.find((event) => event.action === "agent.renamed");
  assert.equal(renamedEvent.traceId, "trace-rename-agent");
  assert.equal(renamedEvent.metadata.agentId, created.id);
  assert.equal(renamedEvent.metadata.nameLength, 4);
  assert.equal(renamedEvent.metadata.workspaceMaterialized, true);
  assert.equal(JSON.stringify(renamedEvent).includes("Vega"), false);
  assert.equal(JSON.stringify(renamedEvent).includes("Orion"), false);
});

test("role and skills changes regenerate four starters and persist each configuration atomically", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-agent-starters-"));
  const databasePath = join(root, "workspace.db");
  const logPath = join(root, "actions.jsonl");
  const generatedFrom = [];
  const generator = async (agents) => {
    generatedFrom.push(agents.map((agent) => ({
      id: agent.id,
      role: agent.systemPrompt,
      skillIds: agent.skills.map((skill) => skill.id),
    })));
    return fakeConversationStarterGenerator(agents);
  };
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async () => assert.fail("business run should not start"),
    new SqliteWorkspaceStore(databasePath),
    {
      actionLogger: new DevelopmentActionLogger(true, logPath),
      conversationStarterGenerator: generator,
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => {
    server.close();
    rmSync(root, { recursive: true, force: true });
  });
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const base = `http://127.0.0.1:${address.port}`;

  const createResponse = await fetch(`${base}/agents`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name: "Lumen",
      roleDescription: "Pomagaj planować domowy budżet.",
    }),
  });
  const created = (await createResponse.json()).agent;
  assert.equal(createResponse.status, 201);
  assert.equal(created.roleDescription, "Pomagaj planować domowy budżet.");
  assert.equal(created.conversationStarters.length, 4);

  const roleText = "Pomagaj planować podróże rodzinne i porównywać trasy.";
  const roleResponse = await fetch(`${base}/agents/${created.id}/role`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-role-update",
    },
    body: JSON.stringify({ roleDescription: roleText }),
  });
  const roleAgent = (await roleResponse.json()).agent;
  assert.equal(roleResponse.status, 200);
  assert.equal(roleAgent.roleDescription, roleText);
  assert.equal(roleAgent.conversationStarters.length, 4);

  const skillsResponse = await fetch(`${base}/agents/${created.id}/skills`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ skillIds: ["web-research", "source-comparison"] }),
  });
  const skillAgent = (await skillsResponse.json()).agent;
  assert.equal(skillsResponse.status, 200);
  assert.deepEqual(skillAgent.assignedSkillIds, ["web-research", "source-comparison"]);
  assert.equal(skillAgent.conversationStarters.length, 4);
  assert.equal(generatedFrom.length, 3);
  assert.equal(generatedFrom[0][0].role, "Pomagaj planować domowy budżet.");
  assert.equal(generatedFrom[1][0].role, roleText);
  assert.deepEqual(generatedFrom[2][0].skillIds, ["web-research", "source-comparison"]);

  const snapshot = new SqliteWorkspaceStore(databasePath).workspace();
  const persisted = snapshot.agents.find((agent) => agent.id === created.id);
  assert.equal(persisted.roleDescription, roleText);
  assert.deepEqual(persisted.assignedSkillIds, ["web-research", "source-comparison"]);
  assert.deepEqual(persisted.conversationStarters, skillAgent.conversationStarters);
  assert.equal(snapshot.recentConversations.length, 0);
  assert.equal(snapshot.recentCompletedTasks.length, 0);
  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  assert.ok(events.some((event) => event.action === "agent.role_updated"
    && event.traceId === "trace-role-update"
    && event.metadata.roleLength === roleText.length
    && event.metadata.starterCount === 4));
  assert.equal(JSON.stringify(events).includes(roleText), false);
});

test("SQLite startup does not overwrite a starter agent's saved name, role or starters", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-starter-persistence-"));
  const databasePath = join(root, "workspace.db");
  try {
    const first = new SqliteWorkspaceStore(databasePath);
    first.renameAgent("starter-atlas", "Atlas prywatny");
    first.updateAgentRole(
      "starter-atlas",
      "Moja zapisana rola",
      ["Pierwszy", "Drugi", "Trzeci", "Czwarty"],
    );
    const reopened = new SqliteWorkspaceStore(databasePath).workspace();
    const atlas = reopened.agents.find((agent) => agent.id === "starter-atlas");
    assert.equal(atlas.name, "Atlas prywatny");
    assert.equal(atlas.roleDescription, "Moja zapisana rola");
    assert.deepEqual(atlas.conversationStarters, ["Pierwszy", "Drugi", "Trzeci", "Czwarty"]);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("invalid roles and invalid generated starters do not partially update an agent", async (context) => {
  const store = new MemoryWorkspaceStore();
  let calls = 0;
  const base = await withServer(
    context,
    async () => assert.fail("business run should not start"),
    {
      conversationStarterGenerator: async (agents) => {
        calls += 1;
        return {
          startersByAgentId: Object.fromEntries(agents.map((agent) => [
            agent.id,
            ["Duplikat", "Duplikat", "Trzecia", "Czwarta"],
          ])),
          toolEventCount: 0,
        };
      },
    },
    store,
  );
  const before = store.agent("starter-atlas");
  const invalidRole = await fetch(`${base}/agents/starter-atlas/role`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ roleDescription: " " }),
  });
  assert.equal(invalidRole.status, 400);
  assert.equal((await invalidRole.json()).error, "invalid_agent_role");
  assert.equal(calls, 0);

  const generationFailure = await fetch(`${base}/agents/starter-atlas/role`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ roleDescription: "Nowa poufna rola testowa" }),
  });
  assert.equal(generationFailure.status, 502);
  assert.equal((await generationFailure.json()).error, "starter_generation_failed");
  const after = store.agent("starter-atlas");
  assert.equal(after.systemPrompt, before.systemPrompt);
  assert.deepEqual(after.conversationStarters, before.conversationStarters);
});

test("one agent configuration generation blocks a concurrent role or skills save", async (context) => {
  let releaseGeneration;
  const generationStarted = new Promise((resolve) => {
    releaseGeneration = resolve;
  });
  let generatorEntered;
  const entered = new Promise((resolve) => { generatorEntered = resolve; });
  const base = await withServer(
    context,
    async () => assert.fail("business run should not start"),
    {
      conversationStarterGenerator: async (agents) => {
        generatorEntered();
        await generationStarted;
        return fakeConversationStarterGenerator(agents);
      },
    },
  );
  const first = fetch(`${base}/agents/starter-atlas/role`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ roleDescription: "Nowa rola Atlasa" }),
  });
  await entered;
  const concurrent = await fetch(`${base}/agents/starter-atlas/skills`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ skillIds: ["writing"] }),
  });
  assert.equal(concurrent.status, 409);
  assert.equal((await concurrent.json()).error, "agent_busy");
  releaseGeneration();
  assert.equal((await first).status, 200);
});

test("starter backfill retries after auth recovery and conditionally preserves a newer agent config", async (context) => {
  let clock = new Date("2026-09-09T08:00:00.000Z");
  let calls = 0;
  let releaseSecond;
  const secondReleased = new Promise((resolve) => { releaseSecond = resolve; });
  let secondEntered;
  const entered = new Promise((resolve) => { secondEntered = resolve; });
  const store = new MemoryWorkspaceStore();
  const base = await withServer(
    context,
    async () => assert.fail("business run should not start"),
    {
      now: () => clock,
      backfillConversationStarters: true,
      starterBackfillRetryMs: 30_000,
      conversationStarterGenerator: async (agents) => {
        calls += 1;
        if (calls === 1) throw new Error("not_authenticated");
        secondEntered();
        await secondReleased;
        return fakeConversationStarterGenerator(agents);
      },
    },
    store,
  );
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(calls, 1);
  const firstWorkspace = await (await fetch(`${base}/workspace`)).json();
  assert.equal(firstWorkspace.agents.every((agent) => agent.conversationStarters.length === 0), true);
  assert.equal(calls, 1);

  clock = new Date("2026-09-09T08:00:31.000Z");
  const retryWorkspace = await (await fetch(`${base}/workspace`)).json();
  assert.equal(retryWorkspace.agents.every((agent) => agent.conversationStarters.length === 0), true);
  await entered;
  store.updateAgentRole("starter-atlas", "Nowsza rola", ["A", "B", "C", "D"]);
  releaseSecond();
  await new Promise((resolve) => setImmediate(resolve));
  const completed = await (await fetch(`${base}/workspace`)).json();
  assert.equal(calls, 2);
  assert.deepEqual(
    completed.agents.find((agent) => agent.id === "starter-atlas").conversationStarters,
    ["A", "B", "C", "D"],
  );
  assert.equal(
    completed.agents.filter((agent) => agent.id !== "starter-atlas")
      .every((agent) => agent.conversationStarters.length === 4),
    true,
  );
  assert.equal(completed.recentConversations.length, 0);
  assert.equal(completed.recentCompletedTasks.length, 0);
});

test("metadata generator command is isolated and parser rejects any tool event", () => {
  const args = conversationStarterCodexArgs("/private/schema.json", "/private/empty-work");
  assert.ok(args.includes("--ephemeral"));
  assert.ok(args.includes("--ignore-user-config"));
  assert.ok(args.includes("--ignore-rules"));
  assert.deepEqual(
    args.filter((argument, index) => args[index - 1] === "--disable"),
    ["shell_tool", "unified_exec", "shell_snapshot", "sleep_tool"],
  );
  assert.equal(args.includes("--dangerously-bypass-approvals-and-sandbox"), false);
  assert.equal(args[args.indexOf("--sandbox") + 1], "read-only");
  assert.equal(args[args.indexOf("--cd") + 1], "/private/empty-work");
  const environment = conversationStarterCodexEnvironment(
    "/original/home",
    "/isolated/home",
    {
      PATH: "/usr/bin",
      CODEX_HOME: "/auth/codex",
      MOBILE_BOT_AGENT_ID: "must-not-propagate",
    },
  );
  assert.equal(environment.HOME, "/isolated/home");
  assert.equal(environment.CODEX_HOME, "/auth/codex");
  assert.equal(environment.PATH, "/usr/bin");
  assert.equal(environment.MOBILE_BOT_AGENT_ID, undefined);

  const store = new MemoryWorkspaceStore();
  const agent = store.agent("starter-atlas");
  const payload = JSON.stringify({
    agents: [{
      agentId: agent.id,
      starters: ["Porównaj źródła", "Sprawdź zmianę", "Przygotuj monitoring", "Zbierz fakty"],
    }],
  });
  const valid = parseConversationStarterCodexOutput(
    [agent],
    JSON.stringify({ type: "item.completed", item: { type: "agent_message", text: payload } }),
  );
  assert.equal(valid.toolEventCount, 0);
  assert.equal(valid.startersByAgentId[agent.id].length, 4);
  assert.throws(() => parseConversationStarterCodexOutput(
    [agent],
    [
      JSON.stringify({ type: "item.completed", item: { type: "command_execution" } }),
      JSON.stringify({ type: "item.completed", item: { type: "agent_message", text: payload } }),
    ].join("\n"),
  ), /starter_generation_used_tool/);
});

test("agent conversation pages keep history beyond the global workspace limit", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-conversation-pages-"));
  context.after(() => rmSync(root, { recursive: true, force: true }));
  const stores = [
    ["memory", new MemoryWorkspaceStore()],
    ["sqlite", new SqliteWorkspaceStore(join(root, "workspace.db"))],
  ];
  for (const [kind, store] of stores) {
    const atlasIds = [];
    for (let index = 0; index < 7; index += 1) {
      atlasIds.push(store.prepareRun("starter-atlas", `Atlas ${index}`).conversation.id);
    }
    for (let index = 0; index < 35; index += 1) {
      store.prepareRun("starter-nova", `Nova ${index}`);
    }
    const server = createHostServer(
      () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
      async () => assert.fail("business run should not start"),
      store,
    );
    server.listen(0, "127.0.0.1");
    await once(server, "listening");
    context.after(() => server.close());
    const address = server.address();
    assert.ok(address && typeof address === "object");
    const base = `http://127.0.0.1:${address.port}`;

    const globalWorkspace = await (await fetch(`${base}/workspace`)).json();
    assert.equal(globalWorkspace.recentConversations.length, 30, kind);
    const firstResponse = await fetch(`${base}/agents/starter-atlas/conversations?offset=0&limit=5`);
    const first = await firstResponse.json();
    assert.equal(firstResponse.status, 200, kind);
    assert.equal(first.conversations.length, 5, kind);
    assert.equal(first.hasMore, true, kind);
    assert.ok(first.conversations.every((conversation) => conversation.agentId === "starter-atlas"), kind);

    const second = await (await fetch(
      `${base}/agents/starter-atlas/conversations?offset=5&limit=5`,
    )).json();
    assert.equal(second.conversations.length, 2, kind);
    assert.equal(second.hasMore, false, kind);
    assert.deepEqual(
      new Set([...first.conversations, ...second.conversations].map((conversation) => conversation.id)),
      new Set(atlasIds),
      kind,
    );
    const invalid = await fetch(`${base}/agents/starter-atlas/conversations?offset=-1&limit=51`);
    assert.equal(invalid.status, 400, kind);
    assert.equal((await invalid.json()).error, "invalid_conversation_page", kind);
    const missing = await fetch(`${base}/agents/missing-agent/conversations`);
    assert.equal(missing.status, 404, kind);
    assert.equal((await missing.json()).error, "agent_not_found", kind);
  }
});

test("every agent receives the current single owner profile on its next run", () => {
  const store = new MemoryWorkspaceStore();
  const firstProfile = "Mam na imię Alicja i interesuję się fotografią.";
  store.updateOwnerProfile(firstProfile);

  for (const agent of store.workspace().agents) {
    const prepared = store.prepareRun(agent.id, "Sprawdź kontekst właściciela");
    assert.equal(prepared.agent.ownerProfile, firstProfile, agent.id);
    assert.match(renderAgentInstructions(prepared.agent), /Alicja/);
  }

  store.updateOwnerProfile("Nowy opis właściciela");
  const nextRun = store.prepareRun("starter-atlas", "Sprawdź zmianę profilu");
  assert.equal(nextRun.agent.ownerProfile, "Nowy opis właściciela");
});

test("15-minute automation is idempotent, visible and runs again without a user request", async (context) => {
  let clock = new Date("2026-09-07T10:00:00.000Z");
  let base;
  const prompts = [];
  const terminalReturns = [];
  let scheduledResolve;
  const scheduled = new Promise((resolve) => { scheduledResolve = resolve; });
  let returnedResolve;
  const returned = new Promise((resolve) => { returnedResolve = resolve; });
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async (input) => {
      prompts.push(input.prompt);
      if (input.prompt.startsWith("[Automation ")) {
        await fetch(`${base}/v5/executor/events`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ runId: input.runId, ok: true, state: "READY" }),
        });
        scheduledResolve();
      }
      return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc325e", reply: "Najniższa cena: 99 zł" };
    },
    new MemoryWorkspaceStore(),
    {
      schedulerPollMs: 5,
      now: () => clock,
      runTerminalReturner: async (returnContext) => {
        terminalReturns.push(returnContext);
        returnedResolve();
      },
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => server.close());
  const address = server.address();
  assert.ok(address && typeof address === "object");
  base = `http://127.0.0.1:${address.port}`;

  const firstRun = await (await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "agent-shopping", prompt: "Monitoruj produkt" }),
  })).json();
  const request = {
    agentId: "agent-shopping",
    conversationId: firstRun.conversationId,
    name: "Cena: test",
    prompt: "Sprawdź produkt testowy na Allegro.",
    intervalMinutes: 15,
  };
  const firstCreate = await fetch(`${base}/automations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  const firstBody = await firstCreate.json();
  assert.equal(firstCreate.status, 201);
  assert.equal(firstBody.automation.intervalMinutes, 15);
  assert.equal(firstBody.automation.nextRunAt, "2026-09-07T10:15:00.000Z");

  const secondCreate = await fetch(`${base}/automations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  assert.equal(secondCreate.status, 200);
  assert.equal((await secondCreate.json()).created, false);

  clock = new Date("2026-09-07T10:15:01.000Z");
  await Promise.race([
    scheduled,
    new Promise((_, reject) => setTimeout(() => reject(new Error("scheduled run did not start")), 1_000)),
  ]);
  await Promise.race([
    returned,
    new Promise((_, reject) => setTimeout(() => reject(new Error("scheduled run did not return")), 1_000)),
  ]);
  assert.equal(prompts.length, 2);
  assert.match(prompts[1], /^\[Automation /);
  assert.match(prompts[1], /Do not create another automation/);
  assert.equal(terminalReturns.length, 1);
  assert.equal(terminalReturns[0].automationId, firstBody.automation.id);
  assert.equal(terminalReturns[0].agentId, "agent-shopping");
  assert.equal(terminalReturns[0].conversationId, firstRun.conversationId);
});

test("automation enabled endpoint is idempotent, validates input and logs no prompt", async (context) => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-automation-enabled-"));
  const logPath = join(root, "actions.jsonl");
  const clock = new Date("2026-09-07T10:00:00.000Z");
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async () => ({ threadId: "01a07c4c-7184-7173-8b49-c64c82fc3261", reply: "Gotowe" }),
    new MemoryWorkspaceStore(),
    {
      schedulerPollMs: 60_000,
      now: () => clock,
      actionLogger: new DevelopmentActionLogger(true, logPath, () => clock),
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => {
    server.close();
    rmSync(root, { recursive: true, force: true });
  });
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const base = `http://127.0.0.1:${address.port}`;
  const initial = await (await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "agent-shopping", prompt: "Utwórz monitoring" }),
  })).json();
  const secretPrompt = "Poufny opis monitorowanego produktu";
  const created = await (await fetch(`${base}/automations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      agentId: "agent-shopping",
      conversationId: initial.conversationId,
      name: "Test switcha",
      prompt: secretPrompt,
      intervalMinutes: 15,
    }),
  })).json();

  const turnOff = () => fetch(`${base}/automations/${created.automation.id}/enabled`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-Mobile-Bot-Trace-Id": "trace-disable-automation",
    },
    body: JSON.stringify({ enabled: false }),
  });
  const first = await turnOff();
  const firstBody = await first.json();
  assert.equal(first.status, 200);
  assert.equal(firstBody.automation.status, "paused");
  const repeated = await turnOff();
  const repeatedBody = await repeated.json();
  assert.equal(repeated.status, 200);
  assert.equal(repeatedBody.automation.status, "paused");
  assert.equal(repeatedBody.automation.nextRunAt, firstBody.automation.nextRunAt);
  const workspace = await (await fetch(`${base}/workspace`)).json();
  assert.equal(workspace.automations.find((item) => item.id === created.automation.id).status, "paused");

  const invalid = await fetch(`${base}/automations/${created.automation.id}/enabled`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled: "false" }),
  });
  assert.equal(invalid.status, 400);
  assert.equal((await invalid.json()).error, "invalid_automation_enabled");
  const missing = await fetch(`${base}/automations/missing-automation/enabled`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled: true }),
  });
  assert.equal(missing.status, 404);
  assert.equal((await missing.json()).error, "automation_not_found");

  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  const toggles = events.filter((event) => event.action === "automation.enabled_updated");
  assert.equal(toggles.length, 2);
  assert.ok(toggles.every((event) =>
    event.traceId === "trace-disable-automation" &&
    event.metadata.automationId === created.automation.id &&
    event.metadata.enabled === false &&
    event.metadata.status === "paused"
  ));
  assert.equal(JSON.stringify(toggles).includes(secretPrompt), false);
});

test("turning a running automation off and on does not start a parallel run", async (context) => {
  let clock = new Date("2026-09-07T10:00:00.000Z");
  let base = "";
  let scheduledRuns = 0;
  let startedResolve;
  const started = new Promise((resolve) => { startedResolve = resolve; });
  let releaseResolve;
  const release = new Promise((resolve) => { releaseResolve = resolve; });
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async (input) => {
      if (input.automationId) {
        scheduledRuns += 1;
        startedResolve();
        await release;
        await fetch(`${base}/v5/executor/events`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ runId: input.runId, ok: true, state: "READY" }),
        });
      }
      return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc3262", reply: "Gotowe" };
    },
    new MemoryWorkspaceStore(),
    { schedulerPollMs: 60_000, now: () => clock },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => {
    releaseResolve();
    server.close();
  });
  const address = server.address();
  assert.ok(address && typeof address === "object");
  base = `http://127.0.0.1:${address.port}`;
  const initial = await (await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "agent-shopping", prompt: "Utwórz monitoring" }),
  })).json();
  const created = await (await fetch(`${base}/automations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      agentId: "agent-shopping",
      conversationId: initial.conversationId,
      name: "Brak równoległego runu",
      prompt: "Sprawdź jeden raz",
      intervalMinutes: 15,
    }),
  })).json();
  const endpoint = `${base}/automations/${created.automation.id}/enabled`;

  clock = new Date("2026-09-07T10:15:01.000Z");
  await fetch(`${base}/v5/scheduler/reconcile`, { method: "POST" });
  await started;
  const disable = await fetch(endpoint, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled: false }),
  });
  assert.equal((await disable.json()).automation.status, "paused");
  const enable = await fetch(endpoint, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled: true }),
  });
  assert.equal((await enable.json()).automation.status, "active");
  await fetch(`${base}/v5/scheduler/reconcile`, { method: "POST" });
  assert.equal(scheduledRuns, 1);

  releaseResolve();
  let workspace;
  for (let attempt = 0; attempt < 30; attempt += 1) {
    workspace = await (await fetch(`${base}/workspace`)).json();
    if (workspace.automations[0].lastOutcome === "completed") break;
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
  assert.equal(scheduledRuns, 1);
  assert.equal(workspace.automations[0].status, "active");
  assert.equal(workspace.automations[0].lastOutcome, "completed");
  assert.equal(workspace.automations[0].nextRunAt, "2026-09-07T10:30:00.000Z");
});

test("Memory and SQLite keep paused automations visible and finish a disabled running task as paused", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-automation-toggle-store-"));
  const databasePath = join(root, "workspace.db");
  const stores = [
    { label: "memory", store: new MemoryWorkspaceStore() },
    { label: "sqlite", store: new SqliteWorkspaceStore(databasePath) },
  ];
  let sqliteAutomationId = "";
  try {
    for (const { label, store } of stores) {
      const createdAt = new Date("2026-09-07T10:00:00.000Z");
      const prepared = store.prepareRun("agent-shopping", `Utwórz monitoring ${label}`);
      const automation = store.createAutomation({
        agentId: "agent-shopping",
        conversationId: prepared.conversation.id,
        name: `Monitoring ${label}`,
        prompt: `Sprawdź produkt ${label}`,
        intervalMinutes: 15,
      }, createdAt).automation;
      if (label === "sqlite") sqliteAutomationId = automation.id;

      const disabled = store.setAutomationEnabled(
        automation.id,
        false,
        new Date("2026-09-07T10:01:00.000Z"),
      );
      assert.equal(disabled.status, "paused", label);
      assert.equal(store.workspace().automations.find((item) => item.id === automation.id).status, "paused", label);
      const repeated = store.setAutomationEnabled(
        automation.id,
        false,
        new Date("2026-09-07T10:02:00.000Z"),
      );
      assert.equal(repeated.nextRunAt, "2026-09-07T10:15:00.000Z", label);

      const futureEnabled = store.setAutomationEnabled(
        automation.id,
        true,
        new Date("2026-09-07T10:03:00.000Z"),
      );
      assert.equal(futureEnabled.status, "active", label);
      assert.equal(futureEnabled.nextRunAt, "2026-09-07T10:15:00.000Z", label);
      store.setAutomationEnabled(automation.id, false, new Date("2026-09-07T10:04:00.000Z"));
      const overdueEnabled = store.setAutomationEnabled(
        automation.id,
        true,
        new Date("2026-09-07T10:47:00.000Z"),
      );
      assert.equal(overdueEnabled.nextRunAt, "2026-09-07T10:45:00.000Z", label);
      assert.equal(store.dueAutomations(new Date("2026-09-07T10:47:00.000Z")).length, 1, label);
      assert.equal(store.markAutomationRunning(automation.id, new Date("2026-09-07T10:47:00.000Z")), true, label);
      store.setAutomationEnabled(automation.id, false, new Date("2026-09-07T10:48:00.000Z"));
      store.finishAutomation(
        automation.id,
        "completed",
        "Bieżące wykonanie zakończone",
        new Date("2026-09-07T10:49:00.000Z"),
      );
      const finished = store.workspace().automations.find((item) => item.id === automation.id);
      assert.equal(finished.status, "paused", label);
      assert.equal(finished.lastOutcome, "completed", label);
      assert.equal(finished.nextRunAt, "2026-09-07T11:00:00.000Z", label);
      assert.equal(store.dueAutomations(new Date("2026-09-07T11:01:00.000Z")).length, 0, label);
    }

    const restored = new SqliteWorkspaceStore(databasePath).workspace();
    const restoredAutomation = restored.automations.find((item) => item.id === sqliteAutomationId);
    assert.equal(restoredAutomation.status, "paused");
    assert.equal(restoredAutomation.lastOutcome, "completed");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("locked phone defers work and unlock runs only the latest due occurrence", async (context) => {
  let clock = new Date("2026-09-07T10:00:00.000Z");
  let readiness = "WAITING_FOR_UNLOCK";
  const logRoot = mkdtempSync(join(tmpdir(), "mobile-bot-propagation-log-"));
  const logPath = join(logRoot, "actions.jsonl");
  const actionLogger = new DevelopmentActionLogger(true, logPath, () => clock);
  const readinessContexts = [];
  context.after(() => rmSync(logRoot, { recursive: true, force: true }));
  let scheduledStartedResolve;
  const scheduledStarted = new Promise((resolve) => { scheduledStartedResolve = resolve; });
  let releaseScheduledResolve;
  const releaseScheduled = new Promise((resolve) => { releaseScheduledResolve = resolve; });
  let base;
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async (input) => {
      if (input.prompt.startsWith("[Automation ")) {
        scheduledStartedResolve();
        await releaseScheduled;
        await fetch(`${base}/v5/executor/events`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ runId: input.runId, ok: true, state: "READY" }),
        });
      }
      return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc325f", reply: "Aktualna cena: 89 zł" };
    },
    new MemoryWorkspaceStore(),
    {
      schedulerPollMs: 60_000,
      now: () => clock,
      deviceReadinessProvider: async (readinessContext) => {
        readinessContexts.push(readinessContext);
        return { state: readiness };
      },
      actionLogger,
    },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => server.close());
  const address = server.address();
  assert.ok(address && typeof address === "object");
  base = `http://127.0.0.1:${address.port}`;
  const firstRun = await (await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "agent-shopping", prompt: "Monitoruj kabel" }),
  })).json();
  await fetch(`${base}/automations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      agentId: "agent-shopping",
      conversationId: firstRun.conversationId,
      name: "Najtańszy kabel USB-C",
      prompt: "Sprawdź aktualną cenę kabla.",
      intervalMinutes: 15,
    }),
  });

  clock = new Date("2026-09-07T11:00:00.000Z");
  await fetch(`${base}/v5/scheduler/reconcile`, {
    method: "POST",
    headers: { "X-Mobile-Bot-Trace-Id": "trace-locked" },
  });
  let workspace = await (await fetch(`${base}/workspace`)).json();
  assert.equal(workspace.automations[0].waitingSince, "2026-09-07T11:00:00.000Z");
  assert.equal(workspace.automations[0].deferredReason, "WAITING_FOR_UNLOCK");
  assert.equal(workspace.automations[0].scheduledAt, "2026-09-07T11:00:00.000Z");

  readiness = "READY";
  clock = new Date("2026-09-07T11:02:00.000Z");
  await fetch(`${base}/v5/scheduler/reconcile`, {
    method: "POST",
    headers: { "X-Mobile-Bot-Trace-Id": "trace-unlocked" },
  });
  await scheduledStarted;
  workspace = await (await fetch(`${base}/workspace`)).json();
  assert.equal(workspace.automations[0].status, "running");
  assert.equal(workspace.automations[0].scheduledAt, "2026-09-07T11:00:00.000Z");
  assert.equal(workspace.automations[0].delayMillis, 120_000);
  assert.equal(workspace.automations[0].skippedOccurrences, 3);
  assert.equal(workspace.automations[0].waitingSince, null);
  releaseScheduledResolve();
  for (let attempt = 0; attempt < 30; attempt += 1) {
    workspace = await (await fetch(`${base}/workspace`)).json();
    if (workspace.automations[0].lastOutcome === "completed") break;
    await new Promise((resolve) => setTimeout(resolve, 10));
  }

  assert.equal(workspace.automations[0].lastOutcome, "completed");
  assert.equal(readinessContexts.length, 2);
  assert.ok(readinessContexts.every((item) => item.agentId === "agent-shopping"));
  assert.ok(readinessContexts.every((item) => item.automationId === workspace.automations[0].id));
  const events = readFileSync(logPath, "utf8").trim().split("\n").map(JSON.parse);
  const requiredActions = [
    HOST_DEVELOPMENT_ACTIONS.SCHEDULER_RECONCILE_STARTED,
    HOST_DEVELOPMENT_ACTIONS.DEVICE_READINESS_OBSERVED,
    HOST_DEVELOPMENT_ACTIONS.AUTOMATION_DEFERRED,
    HOST_DEVELOPMENT_ACTIONS.AUTOMATION_RUN_CLAIMED,
    HOST_DEVELOPMENT_ACTIONS.CODEX_RUN_STARTED,
    HOST_DEVELOPMENT_ACTIONS.EXECUTOR_EVENT_RECEIVED,
    HOST_DEVELOPMENT_ACTIONS.CODEX_RUN_COMPLETED,
    HOST_DEVELOPMENT_ACTIONS.AUTOMATION_RUN_COMPLETED,
    HOST_DEVELOPMENT_ACTIONS.SCHEDULER_RECONCILE_COMPLETED,
  ];
  assert.ok(requiredActions.every((action) => events.some((event) => event.action === action)));
  const agentEvents = events.filter((event) => event.metadata.agentId);
  assert.ok(agentEvents.every((event) => event.metadata.agentId === "agent-shopping"));
  const lockedChildren = events.filter((event) => event.parentTraceId === "trace-locked");
  const unlockedChildren = events.filter((event) => event.parentTraceId === "trace-unlocked");
  assert.ok(lockedChildren.some((event) => event.action === HOST_DEVELOPMENT_ACTIONS.AUTOMATION_DEFERRED));
  assert.ok(unlockedChildren.some((event) => event.action === HOST_DEVELOPMENT_ACTIONS.CODEX_RUN_COMPLETED));
  assert.equal(new Set(events.map((event) => event.eventId)).size, events.length);
});

test("scheduled model text cannot override a failed device readback", async (context) => {
  let clock = new Date("2026-09-07T10:00:00.000Z");
  let base;
  let scheduledFinishedResolve;
  const scheduledFinished = new Promise((resolve) => { scheduledFinishedResolve = resolve; });
  const server = createHostServer(
    () => ({ installed: true, version: "codex-cli 0.153.4", authenticated: true }),
    async (input) => {
      if (input.prompt.startsWith("[Automation ")) {
        await fetch(`${base}/v5/executor/events`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ runId: input.runId, ok: false, state: "PACKAGE_NOT_ALLOWED" }),
        });
        scheduledFinishedResolve();
        return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc325a", reply: "Gotowe" };
      }
      return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc325a", reply: "Utworzono monitoring" };
    },
    new MemoryWorkspaceStore(),
    { schedulerPollMs: 5, now: () => clock },
  );
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => server.close());
  const address = server.address();
  assert.ok(address && typeof address === "object");
  base = `http://127.0.0.1:${address.port}`;
  const initial = await (await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "agent-shopping", prompt: "Monitoruj test" }),
  })).json();
  await fetch(`${base}/automations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      agentId: "agent-shopping",
      conversationId: initial.conversationId,
      name: "Test readbacku",
      prompt: "Sprawdź test.",
      intervalMinutes: 15,
    }),
  });
  clock = new Date("2026-09-07T10:15:01.000Z");
  await scheduledFinished;
  await new Promise((resolve) => setTimeout(resolve, 20));
  const workspace = await (await fetch(`${base}/workspace`)).json();
  assert.equal(workspace.automations[0].lastOutcome, "failed");
  assert.match(workspace.automations[0].lastSummary, /executor_package_not_allowed/);
  assert.equal(workspace.recentCompletedTasks.length, 1);
});

test("agent instruction file contains identity, owner profile, prompt and assigned skills", () => {
  const instructions = renderAgentInstructions({
    id: "agent-test",
    name: "Orbit",
    initial: "O",
    subtitle: "Test",
    ownerProfile: "Mam na imię Alicja. Projektuję produkty i interesuję się fotografią.",
    systemPrompt: "Odpowiadaj konkretnie.",
    skills: [{ id: "facts", name: "Fakty", summary: "Weryfikuj twierdzenia." }],
    conversationStarters: [],
  });

  assert.match(instructions, /Your name: Orbit/);
  assert.match(instructions, /About the phone's owner: Mam na imię Alicja/);
  assert.match(instructions, /Odpowiadaj konkretnie/);
  assert.match(instructions, /Fakty \[facts\]: Weryfikuj twierdzenia/);
  assert.match(instructions, /canonical set of powers individually assigned/);
  assert.match(instructions, /a "power" is a Codex skill/);
  assert.match(instructions, /Reply in the language the owner writes in\./);
  assert.match(instructions, /\.agents\/skills\/<id>\/SKILL\.md/);
});

test("all agents materialize inherited OKF and every assigned predefined skill", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-inherited-okf-"));
  try {
    const store = new MemoryWorkspaceStore();
    const agents = store.workspace().agents.map((agent) => store.prepareRun(agent.id, "test").agent);
    for (const agent of [...agents, { ...agents[0], id: "empty-test", skills: [] }]) {
      const workspace = join(root, agent.id);
      materializeAgentWorkspace(agent, workspace);
      const context = readFileSync(join(workspace, "AGENTS.md"), "utf8");
      assert.ok(context.includes(agent.id));
      assert.ok(context.includes("okf/index.md"));
      assert.ok(context.includes("agent-workspace-okf"));
      for (const skillId of ["agent-workspace-okf", ...agent.skills.map((skill) => skill.id)]) {
        const localSkill = readFileSync(join(workspace, ".agents", "skills", skillId, "SKILL.md"), "utf8");
        assert.match(localSkill, /<!-- mobile-bot-local-learning -->/);
      }
      for (const skill of agent.skills) assert.ok(context.includes(`[${skill.id}]`));
    }
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test("workspace materialization fails before writing files when a skill definition is missing", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-missing-skill-"));
  const workspace = join(root, "workspace");
  try {
    const agent = new MemoryWorkspaceStore().prepareRun("starter-atlas", "test").agent;
    assert.throws(
      () => materializeAgentWorkspace({
        ...agent,
        skills: [...agent.skills, { id: "missing-definition", name: "Missing", summary: "Missing." }],
      }, workspace),
      /missing_predefined_skill_definition skill=missing-definition/,
    );
    assert.equal(existsSync(workspace), false);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test("repeated workspace setup preserves learned skills and OKF for one agent without changing another", () => {
  const root = mkdtempSync(join(tmpdir(), "mobile-bot-persistent-okf-"));
  try {
    const agent = new MemoryWorkspaceStore().prepareRun("agent-mail", "test").agent;
    const a = join(root, "a"), b = join(root, "b");
    materializeAgentWorkspace(agent, a);
    materializeAgentWorkspace({ ...agent, id: "other-agent" }, b);
    const rel = ".agents/skills/email-reply-assistant/SKILL.md";
    const original = readFileSync(join(b, rel), "utf8");
    const learned = readFileSync(join(a, rel), "utf8") + "\nVerified local workflow correction.\n";
    writeFileSync(join(a, rel), learned);
    mkdirSync(join(a, "okf"));
    writeFileSync(join(a, "okf/index.md"), "# Saved workspace\n");
    writeFileSync(join(a, "control.md"), "Keep this local state.\n");
    materializeAgentWorkspace(agent, a);
    materializeAgentWorkspace(agent, a);
    assert.equal(readFileSync(join(a, rel), "utf8"), learned);
    assert.equal(readFileSync(join(b, rel), "utf8"), original);
    assert.equal(readFileSync(join(a, "okf/index.md"), "utf8"), "# Saved workspace\n");
    assert.equal(readFileSync(join(a, "control.md"), "utf8"), "Keep this local state.\n");
    assert.equal((learned.match(/<!-- mobile-bot-local-learning -->/g) ?? []).length, 1);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test("Programista workspace receives the predefined Android skill and template", async (context) => {
  let received;
  const base = await withServer(context, async (input) => {
    received = input;
    return { threadId: "01a07c4c-7184-7173-8b49-c64c82fc325d", reply: "BUILD_PENDING" };
  });
  const response = await fetch(`${base}/runs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId: "agent-programista", prompt: "Zbuduj zegar" }),
  });
  assert.equal(response.status, 200);
  assert.equal(received.agent.name, "Developer");
  assert.deepEqual(received.agent.skills.map((skill) => skill.id), ["android-app-builder", "phone-operations"]);

  const workspace = mkdtempSync(join(tmpdir(), "mobile-bot-programista-"));
  try {
    materializeAgentWorkspace(received.agent, workspace);
    const instructions = readFileSync(join(workspace, "AGENTS.md"), "utf8");
    const skill = readFileSync(join(workspace, ".agents/skills/android-app-builder/SKILL.md"), "utf8");
    const template = readFileSync(join(
      workspace,
      ".agents/skills/android-app-builder/assets/basic-app-template/app/src/main/java/one/behavio/generated/clock/MainActivity.java",
    ), "utf8");
    assert.match(instructions, /Android app builder \[android-app-builder\]/);
    assert.match(skill, /compile against Android API 36/);
    assert.match(template, /class MainActivity/);
    const localPath = join(workspace, ".agents/skills/android-app-builder/SKILL.md");
    writeFileSync(localPath, skill + "\nUser's verified local note.\n");
    const systemScript = join(workspace, ".agents/skills/android-app-builder/system/scripts/project.mjs");
    writeFileSync(systemScript, "outdated system helper");
    materializeAgentWorkspace(received.agent, workspace);
    assert.match(readFileSync(localPath, "utf8"), /User's verified local note/);
    assert.match(readFileSync(systemScript, "utf8"), /Persistent|function initialize/);
    assert.ok(existsSync(join(workspace, ".agents/skills/phone-operations/SKILL.md")));
  } finally {
    rmSync(workspace, { recursive: true, force: true });
  }
});
