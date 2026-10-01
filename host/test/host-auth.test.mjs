import assert from "node:assert/strict";
import test from "node:test";
import { once } from "node:events";
import { createHostServer, MemoryWorkspaceStore } from "../dist/index.js";

const TOKEN = "a".repeat(64);

async function withServer(apiToken, run) {
  const server = createHostServer(() => ({ authenticated: true }), undefined, new MemoryWorkspaceStore(), { apiToken });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  try {
    await run(`http://127.0.0.1:${server.address().port}`);
  } finally {
    server.close();
  }
}

test("health stays public while every other route requires the device token", async () => {
  await withServer(() => TOKEN, async (base) => {
    assert.equal((await fetch(`${base}/health`)).status, 200);
    assert.equal((await fetch(`${base}/workspace`)).status, 401);
    assert.equal((await fetch(`${base}/workspace`, { headers: { Authorization: "Bearer wrong" } })).status, 401);
    assert.equal((await fetch(`${base}/workspace`, { headers: { Authorization: `Bearer ${TOKEN}` } })).status, 200);
  });
});

test("a cross-site text/plain POST cannot start a run", async () => {
  await withServer(() => TOKEN, async (base) => {
    const response = await fetch(`${base}/runs`, {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body: JSON.stringify({ agentId: "nova", prompt: "open the browser" }),
    });
    assert.equal(response.status, 401);
  });
});

test("a missing token file locks the API instead of opening it", async () => {
  await withServer(() => null, async (base) => {
    assert.equal((await fetch(`${base}/workspace`, { headers: { Authorization: "Bearer " } })).status, 401);
    assert.equal((await fetch(`${base}/health`)).status, 200);
  });
});
