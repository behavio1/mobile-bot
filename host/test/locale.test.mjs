import assert from "node:assert/strict";
import test from "node:test";
import { once } from "node:events";
import { createHostServer, MemoryWorkspaceStore } from "../dist/index.js";
import { localizeAgent, parseLocale } from "../dist/locale.js";

async function withServer(options, run) {
  const server = createHostServer(() => ({ authenticated: true }), undefined, new MemoryWorkspaceStore(), options);
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  try {
    await run(`http://127.0.0.1:${server.address().port}`);
  } finally {
    server.close();
  }
}

const atlas = (body) => body.agents.find((agent) => agent.id === "starter-atlas");

test("phone language selects the language of untouched built-in agent texts", async () => {
  await withServer({}, async (base) => {
    const english = await (await fetch(`${base}/workspace`, { headers: { "Accept-Language": "en" } })).json();
    assert.equal(atlas(english).subtitle, "Research, comparisons and monitoring");
    const polish = await (await fetch(`${base}/workspace`, { headers: { "Accept-Language": "pl" } })).json();
    assert.equal(atlas(polish).subtitle, "Research, porównania i monitoring");
    assert.equal(polish.agents.find((agent) => agent.id === "agent-programista").name, "Programista");
    assert.equal(english.agents.find((agent) => agent.id === "agent-programista").name, "Developer");
  });
});

test("a language change is persisted and texts the owner edited stay as written", async () => {
  const persisted = [];
  await withServer({ initialLocale: "pl", persistLocale: (locale) => persisted.push(locale) }, async (base) => {
    await fetch(`${base}/workspace`, { headers: { "Accept-Language": "en-US,en;q=0.9" } });
    assert.deepEqual(persisted, ["en"]);
  });
  const edited = localizeAgent({
    id: "starter-atlas", name: "Atlas", subtitle: "Mój research", systemPrompt: "Szukaj tylko w polskich źródłach.", skills: [],
  }, "en");
  assert.equal(edited.subtitle, "Mój research");
  assert.equal(edited.systemPrompt, "Szukaj tylko w polskich źródłach.");
});

test("Accept-Language parsing maps every non-Polish language to English", () => {
  assert.equal(parseLocale("pl-PL"), "pl");
  assert.equal(parseLocale("de"), "en");
  assert.equal(parseLocale(""), null);
  assert.equal(parseLocale(undefined), null);
});

test("starters generated before a language change are dropped and generated again", async () => {
  const calls = [];
  let release;
  const firstCall = new Promise((resolve) => { release = resolve; });
  const generator = async (agents, locale) => {
    calls.push(locale);
    if (calls.length === 1) await firstCall;
    return {
      startersByAgentId: Object.fromEntries(agents.map((agent) => [agent.id, [1, 2, 3, 4].map((n) => `${locale} ${n}`)])),
      toolEventCount: 0,
    };
  };
  await withServer({ initialLocale: "pl", conversationStarterGenerator: generator, backfillConversationStarters: true }, async (base) => {
    await new Promise((resolve) => setTimeout(resolve, 20));
    await fetch(`${base}/workspace`, { headers: { "Accept-Language": "en" } });
    release();
    for (let attempt = 0; attempt < 50 && calls.length < 2; attempt += 1) await new Promise((resolve) => setTimeout(resolve, 20));
    await new Promise((resolve) => setTimeout(resolve, 50));
    const body = await (await fetch(`${base}/workspace`, { headers: { "Accept-Language": "en" } })).json();
    assert.deepEqual(calls, ["pl", "en"]);
    assert.equal(atlas(body).conversationStarters[0], "en 1");
  });
});
