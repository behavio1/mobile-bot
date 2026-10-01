import { readFileSync } from "node:fs";

const [command, ...args] = process.argv.slice(2);

function usage(message) {
  process.stderr.write(`${message}\n`);
  process.exit(64);
}

function option(name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

if (command !== "create") usage("supported command: create");
const agentId = process.env.MOBILE_BOT_AGENT_ID;
const conversationId = process.env.MOBILE_BOT_CONVERSATION_ID;
const name = option("--name");
const prompt = option("--prompt");
const intervalMinutes = Number.parseInt(option("--interval-minutes") ?? "", 10);
if (!agentId || !conversationId) usage("this command must run inside a Mobile Bot agent task");
if (!name || !prompt || intervalMinutes !== 15) usage("create requires --name, --prompt and --interval-minutes 15");

let token = "";
try {
  token = readFileSync(`${process.env.HOME ?? "."}/.mobile-bot-ui/device-token`, "utf8").trim();
} catch {
  usage("Mobile Bot device token is missing; open Mobile Bot to reconnect Termux");
}
const response = await fetch("http://127.0.0.1:8767/automations", {
  method: "POST",
  headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json; charset=utf-8" },
  body: JSON.stringify({ agentId, conversationId, name, prompt, intervalMinutes }),
  signal: AbortSignal.timeout(10_000),
});
const text = await response.text();
process.stdout.write(`${text}\n`);
if (!response.ok) process.exit(1);
