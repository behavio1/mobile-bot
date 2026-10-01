import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const clientPath = fileURLToPath(
  new URL("../../app/src/main/assets/phone-client.mjs", import.meta.url),
);
const deviceHost = "127.0.0.1";
const devicePort = 8768;
const testToken = "phone-client-test-token-do-not-print";
const privateBody = "PRIVATE_MESSAGE_BODY_DO_NOT_PRINT";

async function runClient(home, args = ["status"], extraEnv = {}) {
  const child = spawn(process.execPath, [clientPath, ...args], {
    env: {
      ...process.env,
      HOME: home,
      MOBILE_BOT_RUN_ID: "",
      MOBILE_BOT_AUTOMATION_ID: "",
      MOBILE_BOT_AGENT_ID: "",
      MOBILE_BOT_TRACE_ID: "",
      ...extraEnv,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let stdout = "";
  let stderr = "";
  child.stdout.setEncoding("utf8");
  child.stderr.setEncoding("utf8");
  child.stdout.on("data", (chunk) => { stdout += chunk; });
  child.stderr.on("data", (chunk) => { stderr += chunk; });
  const [exitCode] = await once(child, "close");
  return { exitCode, stdout, stderr, parsed: JSON.parse(stdout) };
}

async function withFakeDevice(handler, run) {
  const requests = [];
  const server = createServer((request, response) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      requests.push({
        authorization: request.headers.authorization,
        body: Buffer.concat(chunks).toString("utf8"),
      });
      handler(request, response);
    });
  });
  server.listen(devicePort, deviceHost);
  await once(server, "listening");
  try {
    return await run(requests);
  } finally {
    server.close();
    await once(server, "close");
  }
}

function assertPrivateOutput(result) {
  assert.equal(result.exitCode, 0);
  assert.equal(result.stderr, "");
  assert.doesNotMatch(result.stdout, new RegExp(testToken));
  assert.doesNotMatch(result.stdout, new RegExp(privateBody));
}

test("phone client reports protocol and transport outcomes without printing credentials or request bodies", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "mobile-bot-phone-client-"));
  await mkdir(join(home, ".mobile-bot-ui"));
  await writeFile(join(home, ".mobile-bot-ui", "device-token"), `${testToken}\n`, { mode: 0o600 });

  try {
    const responseCases = [
      {
        name: "empty response",
        response: { status: 200, body: "" },
        expected: { ok: false, state: "EMPTY_RESPONSE", httpStatus: 200 },
      },
      {
        name: "invalid JSON",
        response: { status: 200, body: "not-json" },
        expected: { ok: false, state: "PROTOCOL_ERROR", httpStatus: 200 },
      },
      {
        name: "invalid response shape",
        response: { status: 200, body: "{}" },
        expected: { ok: false, state: "PROTOCOL_ERROR", httpStatus: 200 },
      },
      {
        name: "unauthorized response",
        response: { status: 401, body: JSON.stringify({ ok: false, state: "UNAUTHORIZED", secret: privateBody }) },
        expected: { ok: false, state: "AUTHORIZATION_FAILED", httpStatus: 401 },
      },
      {
        name: "permission denied response",
        response: { status: 403, body: JSON.stringify({ ok: false, state: "FORBIDDEN", secret: privateBody }) },
        expected: { ok: false, state: "PERMISSION_DENIED", httpStatus: 403 },
      },
    ];

    for (const item of responseCases) {
      await t.test(item.name, async () => {
        const result = await withFakeDevice(
          (_request, response) => {
            response.writeHead(item.response.status, { "Content-Type": "application/json" });
            response.end(item.response.body);
          },
          (requests) => runClient(home).then((runResult) => ({ runResult, requests })),
        );
        assert.deepEqual(result.runResult.parsed, item.expected);
        assertPrivateOutput(result.runResult);
        assert.equal(result.requests.length, 1);
        assert.equal(result.requests[0].authorization, `Bearer ${testToken}`);
        assert.deepEqual(JSON.parse(result.requests[0].body), { action: "status" });
      });
    }

    await t.test("app search and launcher navigation are not restricted to named apps", async () => {
      for (const [args, expected] of [
        [["list-apps", "Allegro"], { action: "list_apps", query: "Allegro" }],
        [["list-apps", "Any", "App"], { action: "list_apps", query: "Any App" }],
        [["home"], { action: "home" }],
      ]) {
        const requests = await withFakeDevice((_request, response) => {
          response.writeHead(200, { "Content-Type": "application/json" });
          response.end(JSON.stringify({ ok: true, state: "READY" }));
        }, async (requests) => { await runClient(home, args); return requests; });
        assert.deepEqual(JSON.parse(requests[0].body), expected);
      }
    });

    await t.test("phone control service unavailable", async () => {
      const result = await runClient(home);
      assert.deepEqual(result.parsed, { ok: false, state: "PHONE_CONTROL_UNAVAILABLE" });
      assertPrivateOutput(result);
    });

    await t.test("aborted response body", async () => {
      const result = await withFakeDevice(
        (_request, response) => {
          response.writeHead(200, {
            "Content-Type": "application/json",
            "Content-Length": "1000",
          });
          response.write('{"ok":true,"state":"READY"');
          response.destroy();
        },
        () => runClient(home),
      );
      assert.deepEqual(result.parsed, { ok: false, state: "NETWORK_ERROR" });
      assertPrivateOutput(result);
    });

    await t.test("valid response and private set-text request", async () => {
      const validResponse = {
        ok: true,
        state: "READY",
        observation: { packageName: "com.example.mail", nodes: [] },
      };
      const result = await withFakeDevice(
        (_request, response) => {
          response.writeHead(200, { "Content-Type": "application/json" });
          response.end(JSON.stringify(validResponse));
        },
        (requests) => runClient(home, ["set-text", "Message", privateBody])
          .then((runResult) => ({ runResult, requests })),
      );
      assert.deepEqual(result.runResult.parsed, validResponse);
      assertPrivateOutput(result.runResult);
      assert.equal(result.requests.length, 1);
      assert.equal(result.requests[0].authorization, `Bearer ${testToken}`);
      assert.deepEqual(JSON.parse(result.requests[0].body), {
        action: "set_text",
        selector: "Message",
        text: privateBody,
        index: 0,
      });
    });

    await t.test("explicit empty set-text selector", async () => {
      const validResponse = {
        ok: true,
        state: "READY",
        observation: { packageName: "one.behavio.mobilebotui", nodes: [] },
      };
      const result = await withFakeDevice(
        (_request, response) => {
          response.writeHead(200, { "Content-Type": "application/json" });
          response.end(JSON.stringify(validResponse));
        },
        (requests) => runClient(home, ["set-text", "", privateBody, "0"])
          .then((runResult) => ({ runResult, requests })),
      );
      assert.deepEqual(result.runResult.parsed, validResponse);
      assertPrivateOutput(result.runResult);
      assert.deepEqual(JSON.parse(result.requests[0].body), {
        action: "set_text",
        selector: "",
        text: privateBody,
        index: 0,
      });
    });

    await t.test("read-only phone mode is carried to mutating click and set-text requests", async () => {
      for (const [args, expected] of [
        [["click", "Rename", "0"], { action: "click", text: "Rename", index: 0, readOnly: true }],
        [["set-text", "Device name", "changed", "0"], {
          action: "set_text", selector: "Device name", text: "changed", index: 0, readOnly: true,
        }],
      ]) {
        const requests = await withFakeDevice((_request, response) => {
          response.writeHead(200, { "Content-Type": "application/json" });
          response.end(JSON.stringify({ ok: false, state: "READ_ONLY_ACTION_REJECTED" }));
        }, async (requests) => {
          await runClient(home, args, { MOBILE_BOT_PHONE_READ_ONLY: "1" });
          return requests;
        });
        assert.deepEqual(JSON.parse(requests[0].body), expected);
      }
    });

    await t.test("missing device authorization", async () => {
      const unauthorizedHome = await mkdtemp(join(tmpdir(), "mobile-bot-phone-client-unauthorized-"));
      try {
        const result = await runClient(unauthorizedHome);
        assert.deepEqual(result.parsed, { ok: false, state: "AUTHORIZATION_UNAVAILABLE" });
        assertPrivateOutput(result);
      } finally {
        await rm(unauthorizedHome, { recursive: true, force: true });
      }
    });
  } finally {
    await rm(home, { recursive: true, force: true });
  }
});
