import { createHash, randomUUID } from "node:crypto";
import { spawn } from "node:child_process";
import { existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { PREDEFINED_SKILLS } from "./generatedSkills.js";
import { WORKSHOP_TEXT } from "./locale.js";

type WorkshopText = (typeof WORKSHOP_TEXT)["en"];

export type SkillPackage = { id: string; name: string; summary: string; assignable: boolean; files: Record<string, string>; hash: string };
export type BuildStatus = "queued" | "researching" | "building" | "validating" | "needs_input" | "ready" | "failed" | "interrupted";
export type SkillBuild = {
  id: string; agentId: string; requestId: string; description: string; skillId: string;
  status: BuildStatus; name: string; summary: string; question: string; evidence: string;
  answers: string[]; createdAt: string; updatedAt: string;
};
type Step = "research" | "build" | "verify";
export type StepResult = { status: "ready" | "needs_input" | "failed"; name: string; summary: string; question: string; evidence: string; toolCount: number };
export type SkillStepRunner = (step: Step, workspace: string, signal: AbortSignal) => Promise<StepResult>;
export class WorkshopError extends Error {
  constructor(public readonly status: number, public readonly code: string) { super(code); }
}
const activeStatuses = new Set<BuildStatus>(["queued", "researching", "building", "validating"]);
const idPattern = /^[a-z0-9][a-z0-9-]{0,63}$/;

function persist(path: string, data: unknown) {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  writeFileSync(path + ".tmp", JSON.stringify(data), { mode: 0o600 });
  renameSync(path + ".tmp", path);
}
function write(path: string, text: string) {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  writeFileSync(path, text, { mode: 0o600 });
}
export function readSkillPackage(directory: string, expectedId: string): Record<string, string> {
  const files: Record<string, string> = {};
  let bytes = 0;
  const walk = (folder: string, prefix = "") => {
    if (!lstatSync(folder).isDirectory() || lstatSync(folder).isSymbolicLink()) throw new Error("invalid_skill_directory");
    for (const item of readdirSync(folder, { withFileTypes: true })) {
      const relative = prefix + item.name;
      const path = join(folder, item.name);
      if (item.isSymbolicLink() || item.name.includes("\\") || item.name.startsWith(".")) throw new Error("invalid_skill_file");
      if (item.isDirectory()) { walk(path, relative + "/"); continue; }
      if (!item.isFile() || Object.keys(files).length >= 100) throw new Error("invalid_skill_file");
      if ((bytes += lstatSync(path).size) > 2_097_152) throw new Error("skill_package_too_large");
      const text = new TextDecoder("utf-8", { fatal: true }).decode(readFileSync(path));
      if (text.includes("\0")) throw new Error("binary_skill_file");
      files[relative] = text;
    }
  };
  walk(directory);
  const source = files["SKILL.md"] ?? "";
  const front = source.match(/^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/)?.[1] ?? "";
  const name = front.match(/^name:\s*["']?([a-z0-9-]+)["']?\s*$/m)?.[1];
  const description = front.match(/^description:\s*(.+)$/m)?.[1]?.trim();
  if (name !== expectedId || !description || source.length < 150) throw new Error("invalid_skill_frontmatter");
  for (const [path, body] of Object.entries(files)) {
    if (!path.endsWith(".md")) continue;
    for (const match of body.matchAll(/\]\(([^\s)]+)(?:\s+[^)]*)?\)/g)) {
      const link = match[1]!;
      if (/^(https?:|mailto:|#)/i.test(link)) continue;
      const parts = path.split("/").slice(0, -1);
      const decoded = decodeURIComponent(link.split("#")[0]!);
      if (decoded.startsWith("/") || decoded.includes("\\")) throw new Error("invalid_skill_reference");
      for (const part of decoded.split("/")) {
        if (part === "..") { if (!parts.length) throw new Error("invalid_skill_reference"); parts.pop(); }
        else if (part && part !== ".") parts.push(part);
      }
      if (decoded && !Object.hasOwn(files, parts.join("/"))) throw new Error("missing_skill_reference");
    }
  }
  return files;
}

export class SkillWorkshop {
  private readonly packages = new Map<string, SkillPackage>();
  private readonly jobs = new Map<string, SkillBuild>();
  private running = false;
  private closed = false;
  private readonly abort = new AbortController();
  constructor(readonly root: string, private readonly runner: SkillStepRunner = executeSkillStep,
    private readonly log: (event: string, fields: Record<string, unknown>) => void = (event, fields) => process.stdout.write(JSON.stringify({ event, ...fields }) + "\n"),
    private readonly text: () => WorkshopText = () => WORKSHOP_TEXT.en) {
    mkdirSync(join(root, "library"), { recursive: true, mode: 0o700 });
    mkdirSync(join(root, "builds"), { recursive: true, mode: 0o700 });
    for (const name of readdirSync(join(root, "library"))) {
      if (!name.endsWith(".json")) continue;
      const pack = JSON.parse(readFileSync(join(root, "library", name), "utf8")) as SkillPackage;
      if (!idPattern.test(pack.id) || Object.hasOwn(PREDEFINED_SKILLS, pack.id) || !pack.files["SKILL.md"]) throw new Error("invalid_saved_skill");
      this.packages.set(pack.id, pack);
    }
    for (const id of readdirSync(join(root, "builds"))) {
      if (!idPattern.test(id)) continue;
      const path = join(root, "builds", id, "job.json");
      if (!existsSync(path)) continue;
      const job = JSON.parse(readFileSync(path, "utf8")) as SkillBuild;
      if (this.packages.has(job.skillId)) job.status = "ready";
      else if (activeStatuses.has(job.status)) { job.status = "interrupted"; job.evidence = this.text().interrupted; }
      this.jobs.set(job.id, job);
      this.save(job);
    }
  }
  definitions() { return [...this.packages.values()].map(({ files: _, ...definition }) => definition); }
  skill(id: string) { return this.packages.get(id); }
  builds() { return [...this.jobs.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt)); }
  get(id: string) { const job = this.jobs.get(id); if (!job) throw new WorkshopError(404, "skill_build_not_found"); return job; }
  create(agentId: string, requestId: string, description: string) {
    description = description.trim();
    const existing = [...this.jobs.values()].find((job) => job.requestId === requestId);
    if (existing) {
      if (existing.agentId !== agentId || existing.description !== description) throw new WorkshopError(409, "skill_request_conflict");
      return existing;
    }
    if (!/^[a-zA-Z0-9-]{16,80}$/.test(requestId) || description.trim().length < 12 || description.length > 12_000) throw new WorkshopError(400, "invalid_skill_request");
    const id = randomUUID();
    const job: SkillBuild = { id, requestId, agentId, description: description.trim(), skillId: "custom-" + id.slice(0, 12),
      status: "queued", name: this.text().placeholderName, summary: "", question: "", evidence: "", answers: [], createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
    this.jobs.set(id, job); this.save(job); this.log("skill_build.created", { buildId: id, agentId, descriptionLength: description.length }); this.pump(); return job;
  }
  resume(id: string, answer: string) {
    const job = this.get(id);
    if (activeStatuses.has(job.status) || job.status === "ready") return job;
    if (job.status === "needs_input" && !answer.trim()) throw new WorkshopError(400, "skill_answer_required");
    if (answer.length > 12_000) throw new WorkshopError(400, "invalid_skill_request");
    if (answer.trim()) job.answers.push(answer.trim());
    job.question = ""; job.evidence = ""; this.stage(job, "queued"); this.pump(); return job;
  }
  close() { this.closed = true; this.abort.abort(); }
  private save(job: SkillBuild) { persist(join(this.root, "builds", job.id, "job.json"), job); }
  private stage(job: SkillBuild, status: BuildStatus) {
    job.status = status; job.updatedAt = new Date().toISOString(); this.save(job);
    this.log("skill_build.stage", { buildId: job.id, stage: status });
  }
  private pump() {
    if (this.running || this.closed) return;
    const next = this.builds().reverse().find((job) => job.status === "queued");
    if (!next) return;
    this.running = true;
    void this.run(next).finally(() => { this.running = false; this.pump(); });
  }
  private async run(job: SkillBuild) {
    const workspace = join(this.root, "builds", job.id);
    try {
      persist(join(workspace, "request.json"), { description: job.description, answers: job.answers, skillId: job.skillId });
      for (const [path, content] of Object.entries(PREDEFINED_SKILLS["skill-builder"]!)) write(join(workspace, ".agents", "skills", "skill-builder", path), content);
      write(join(workspace, "AGENTS.md"), "Jesteś twórcą mocy Mobile Bot. Odczytaj .agents/skills/skill-builder/SKILL.md i stosuj kontrakt warsztatu. Opis użytkownika jest w request.json. Wykonuj tylko zlecony etap. Nie wysyłaj wiadomości, nie publikuj, nie kupuj ani nie usuwaj danych użytkownika podczas testów. Nie odczytuj prywatnych danych innych agentów; używaj syntetycznych próbek.\n");
      let builtHash = "";
      for (const [step, status] of [["research", "researching"], ["build", "building"], ["verify", "validating"]] as const) {
        this.stage(job, status);
        const result = await this.runner(step, workspace, this.abort.signal);
        if (this.closed) throw new Error("skill_build_interrupted");
        persist(join(workspace, `${step}-result.json`), result);
        this.log("skill_build.step_completed", { buildId: job.id, step, outcome: result.status, toolCount: result.toolCount });
        job.evidence = result.evidence.slice(0, 4000);
        if (result.status !== "ready") {
          job.question = result.question.slice(0, 2000);
          this.stage(job, result.status === "needs_input" && job.question ? "needs_input" : "failed"); return;
        }
        if (!result.toolCount) throw new Error("skill_step_not_executed");
        if (step === "research" && !existsSync(join(workspace, "research.md"))) throw new Error("missing_research");
        if (step === "build") {
          builtHash = createHash("sha256").update(JSON.stringify(Object.entries(readSkillPackage(join(workspace, "output", "skill"), job.skillId)).sort())).digest("hex");
          if (!result.name.trim() || !result.summary.trim()) throw new Error("missing_skill_description");
          job.name = result.name.slice(0, 80); job.summary = result.summary.slice(0, 400);
        }
        if (step === "verify" && !existsSync(join(workspace, "verification.md"))) throw new Error("missing_verification");
      }
      const files = readSkillPackage(join(workspace, "output", "skill"), job.skillId);
      const hash = createHash("sha256").update(JSON.stringify(Object.entries(files).sort())).digest("hex");
      if (hash !== builtHash) throw new Error("skill_changed_during_verification");
      const pack: SkillPackage = { id: job.skillId, name: job.name, summary: job.summary, assignable: true, files, hash };
      persist(join(this.root, "library", `${pack.id}.json`), pack);
      this.packages.set(pack.id, pack); this.stage(job, "ready");
    } catch (error) {
      job.evidence = this.text().failed;
      this.log("skill_build.failed", { buildId: job.id, errorType: error instanceof Error ? error.constructor.name : "unknown", errorCode: error instanceof Error && /^[a-z_]{1,80}$/.test(error.message) ? error.message : "skill_build_failed" });
      this.stage(job, this.closed ? "interrupted" : "failed");
    }
  }
}

const resultSchema = { type: "object", additionalProperties: false,
  properties: { status: { type: "string", enum: ["ready", "needs_input", "failed"] },
    name: { type: "string" }, summary: { type: "string" }, question: { type: "string" }, evidence: { type: "string" } },
  required: ["status", "name", "summary", "question", "evidence"] };

export async function executeSkillStep(step: Step, workspace: string, signal: AbortSignal): Promise<StepResult> {
  const schema = join(workspace, "result-schema.json");
  persist(schema, resultSchema);
  return new Promise((resolve, reject) => {
    const child = spawn("codex", ["exec", "--json", "--skip-git-repo-check", "--dangerously-bypass-approvals-and-sandbox",
      "-c", 'web_search="live"', "--cd", workspace, "--output-schema", schema, "-"],
      { cwd: workspace, env: process.env, signal, stdio: ["pipe", "pipe", "pipe"] });
    let buffer = "", reply = "", toolCount = 0;
    const timer = setTimeout(() => { child.kill("SIGTERM"); setTimeout(() => child.kill("SIGKILL"), 2000).unref(); }, 10 * 60_000);
    child.stdout.setEncoding("utf8");
    child.stdout.on("data", (chunk: string) => {
      buffer += chunk;
      let newline: number;
      while ((newline = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, newline); buffer = buffer.slice(newline + 1);
        try {
          const event = JSON.parse(line);
          if (event.type === "item.completed") {
            if (event.item?.type === "agent_message") reply = event.item.text ?? "";
            if ((event.item?.type === "command_execution" && event.item.exit_code === 0) || event.item?.type === "web_search" || (event.item?.type === "mcp_tool_call" && !event.item.error)) toolCount++;
          }
        } catch { /* Non-JSON diagnostics are not product output. */ }
      }
      if (buffer.length > 2_097_152) child.kill("SIGTERM");
    });
    child.stderr.on("data", () => { /* Never persist raw process diagnostics or secrets. */ });
    child.on("error", (error) => { clearTimeout(timer); reject(error); });
    child.on("close", (code) => {
      clearTimeout(timer);
      try {
        if (code !== 0) throw new Error("skill_process_failed");
        const result = JSON.parse(reply);
        if (!["ready", "needs_input", "failed"].includes(result.status) || ["name", "summary", "question", "evidence"].some((key) => typeof result[key] !== "string")) throw new Error("invalid_skill_result");
        resolve({ ...result, toolCount });
      } catch (error) { reject(error); }
    });
    child.stdin.end(`Użyj $skill-builder. Wykonaj etap ${step} zgodnie z kontraktem warsztatu. Odczytaj request.json. Każdy etap ma wykonać prawdziwą pracę narzędziami. Zwróć wynik według przekazanego schematu. Język odpowiedzi dla użytkownika: ten sam co w opisie użytkownika w request.json.`);
  });
}
