import { createHash, randomUUID } from "node:crypto";
import { spawnSync, spawn, type ChildProcess } from "node:child_process";
import { createWriteStream, existsSync, mkdirSync, readFileSync, renameSync, statSync, statfsSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

/**
 * The first on-device model is deliberately small.  The URL and digest are
 * immutable release metadata so a partially downloaded file can never be
 * mistaken for a model that is safe to load.
 */
export const LOCAL_AI_MODEL = {
  id: "qwen3.5-0.8b-q4_k_m",
  name: "Qwen3.5 0.8B Q4_K_M",
  fileName: "Qwen3.5-0.8B-Q4_K_M.gguf",
  expectedBytes: 532_517_120,
  sha256: "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
  sourceUrl: "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/6ab461498e2023f6e3c1baea90a8f0fe38ab64d0/Qwen3.5-0.8B-Q4_K_M.gguf",
};

export const LOCAL_AI_ENGINE = {
  id: "llama.cpp",
  version: "b10516",
  archiveUrl: "https://github.com/ggml-org/llama.cpp/releases/download/b10516/llama-b10516-bin-android-arm64.tar.gz",
  sha256: "1d2f78c13ec4a6197506288ba0aa0853d71c1b3048ff771ea37791be7f591cc6",
};

export const LOCAL_AI_PI = {
  package: "@earendil-works/pi-coding-agent",
  version: "0.85.1",
  nodeMinVersion: "22.19.0",
};

export const LOCAL_AI_CONTEXT_WINDOW = 16_384;

export type LocalAiState = {
  status: "not_installed" | "downloading" | "verifying" | "starting" | "ready" | "stopped" | "failed";
  modelId: string;
  modelFile: string | null;
  bytesDownloaded: number;
  totalBytes: number;
  operationId: string | null;
  errorCode: string | null;
  engineVersion: string | null;
  piVersion: string | null;
  updatedAt: string;
};

export type RuntimeKind = "codex" | "pi-local";

export type RuntimeSelection = {
  selected: RuntimeKind;
  updatedAt: string;
};

export type LocalAiStatus = LocalAiState & {
  modelPath: string | null;
  engineReady: boolean;
  piReady: boolean;
  serverPort: number;
};

type LocalAiLog = (event: string, fields: Record<string, unknown>) => void;

function safeReadJson<T>(path: string): T | null {
  try {
    return JSON.parse(readFileSync(path, "utf8")) as T;
  } catch {
    return null;
  }
}

function persistJson(path: string, value: unknown): void {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  const temporary = `${path}.tmp.${process.pid}`;
  writeFileSync(temporary, JSON.stringify(value), { mode: 0o600 });
  renameSync(temporary, path);
}

function isReadyModel(path: string): boolean {
  try {
    return statSync(path).isFile() && statSync(path).size === LOCAL_AI_MODEL.expectedBytes;
  } catch {
    return false;
  }
}

function availableBytes(path: string): number | null {
  try {
    return Number(statfsSync(path).bavail) * Number(statfsSync(path).bsize);
  } catch {
    return null;
  }
}

function codeFromError(error: unknown, fallback: string): string {
  const message = error instanceof Error ? error.message : "";
  return /^[a-z][a-z0-9_]{1,80}$/.test(message) ? message : fallback;
}

export function runtimeSettingsPath(installRoot: string): string {
  return join(installRoot, "runtime-settings.json");
}

export function readRuntimeSelection(installRoot: string): RuntimeSelection {
  const value = safeReadJson<Partial<RuntimeSelection>>(runtimeSettingsPath(installRoot));
  return {
    selected: value?.selected === "pi-local" ? "pi-local" : "codex",
    updatedAt: typeof value?.updatedAt === "string" ? value.updatedAt : new Date(0).toISOString(),
  };
}

export function saveRuntimeSelection(installRoot: string, selected: RuntimeKind): RuntimeSelection {
  const result = { selected, updatedAt: new Date().toISOString() } satisfies RuntimeSelection;
  mkdirSync(installRoot, { recursive: true, mode: 0o700 });
  persistJson(runtimeSettingsPath(installRoot), result);
  return result;
}

export class LocalAiManager {
  private readonly root: string;
  private readonly statePath: string;
  private readonly modelPath: string;
  private readonly partialPath: string;
  private readonly binaryPath: string;
  private readonly apiKeyPath: string;
  private state: LocalAiState;
  private downloadPromise: Promise<LocalAiStatus> | null = null;
  private startPromise: Promise<LocalAiStatus> | null = null;
  private server: ChildProcess | null = null;
  private readonly log: LocalAiLog;
  readonly port: number;

  constructor(
    installRoot: string,
    options: { port?: number; log?: LocalAiLog } = {},
  ) {
    this.root = join(installRoot, "local-ai");
    this.statePath = join(this.root, "install-state.json");
    this.modelPath = join(this.root, "models", LOCAL_AI_MODEL.fileName);
    this.partialPath = `${this.modelPath}.part`;
    this.binaryPath = join(this.root, "bin", "llama-server");
    this.apiKeyPath = join(this.root, "api-key");
    this.port = options.port ?? 8769;
    this.log = options.log ?? (() => undefined);
    mkdirSync(join(this.root, "models"), { recursive: true, mode: 0o700 });
    mkdirSync(join(this.root, "bin"), { recursive: true, mode: 0o700 });
    const saved = safeReadJson<Partial<LocalAiState>>(this.statePath);
    this.state = {
      status: isReadyModel(this.modelPath) ? "stopped" : "not_installed",
      modelId: LOCAL_AI_MODEL.id,
      modelFile: isReadyModel(this.modelPath) ? this.modelPath : null,
      bytesDownloaded: isReadyModel(this.modelPath) ? LOCAL_AI_MODEL.expectedBytes : 0,
      totalBytes: LOCAL_AI_MODEL.expectedBytes,
      operationId: null,
      errorCode: null,
      engineVersion: existsSync(this.binaryPath) ? LOCAL_AI_ENGINE.version : null,
      piVersion: null,
      updatedAt: new Date().toISOString(),
      ...saved,
    };
    if (!isReadyModel(this.modelPath)) {
      this.state.status = "not_installed";
      this.state.modelFile = null;
      this.state.bytesDownloaded = this.partialBytes();
    }
    this.saveState();
  }

  status(): LocalAiStatus {
    const engineReady = existsSync(this.binaryPath);
    const piReady = this.piInstalled();
    return {
      ...this.state,
      piVersion: piReady ? (this.state.piVersion ?? LOCAL_AI_PI.version) : null,
      modelPath: isReadyModel(this.modelPath) ? this.modelPath : null,
      engineReady,
      piReady,
      serverPort: this.port,
    };
  }

  get model(): string {
    return this.modelPath;
  }

  get binary(): string {
    return this.binaryPath;
  }

  get apiKey(): string {
    try {
      return readFileSync(this.apiKeyPath, "utf8").trim();
    } catch {
      return "";
    }
  }

  isReady(): boolean {
    const current = this.status();
    return current.status === "ready" && current.modelPath !== null && current.engineReady && current.piReady;
  }

  async prepare(): Promise<LocalAiStatus> {
    if (this.downloadPromise) return this.downloadPromise;
    this.downloadPromise = this.downloadModel().finally(() => { this.downloadPromise = null; });
    return this.downloadPromise;
  }

  async start(): Promise<LocalAiStatus> {
    if (this.startPromise) return this.startPromise;
    this.startPromise = this.startInternal().finally(() => { this.startPromise = null; });
    return this.startPromise;
  }

  private async startInternal(): Promise<LocalAiStatus> {
    if (!isReadyModel(this.modelPath)) throw new Error("local_ai_model_missing");
    if (!existsSync(this.binaryPath)) throw new Error("local_ai_engine_missing");
    const key = this.apiKey || randomUUID().replaceAll("-", "");
    writeFileSync(this.apiKeyPath, `${key}\n`, { mode: 0o600 });

    // A Host restart can leave a llama-server child alive under Termux. Reuse
    // a healthy listener instead of starting a second process on the same
    // port. Also repair the persisted "stopped" state when a caller retries
    // while the listener is already running.
    if (this.server && !this.server.killed && this.server.exitCode === null && this.server.signalCode === null) {
      if (await this.probeHealth()) return this.markReady();
      this.server.kill("SIGKILL");
      this.server = null;
    }
    if (await this.probeHealth()) return this.markReady();

    this.state = { ...this.state, status: "starting", errorCode: null, updatedAt: new Date().toISOString() };
    this.saveState();
    const args = [
      "--model", this.modelPath,
      "--host", "127.0.0.1",
      "--port", String(this.port),
      "--ctx-size", String(LOCAL_AI_CONTEXT_WINDOW),
      "--threads", "4",
      "--parallel", "1",
      "--temp", "0",
      "--jinja",
      "--no-webui",
      "--api-key-file", this.apiKeyPath,
      "--chat-template-kwargs", '{"enable_thinking":false}',
    ];
    this.log("local_ai.server_start", { modelId: LOCAL_AI_MODEL.id, engineVersion: LOCAL_AI_ENGINE.version, port: this.port });
    this.server = spawn(this.binaryPath, args, {
      cwd: this.root,
      // llama.cpp's Android binaries load the CPU backend from the executable
      // directory, while the backend's shared dependencies live in lib/.
      env: { ...process.env, LD_LIBRARY_PATH: [join(this.root, "bin"), join(this.root, "lib")].join(":") },
      stdio: ["ignore", "pipe", "pipe"],
    });
    this.server.stdout?.on("data", () => undefined);
    this.server.stderr?.on("data", () => undefined);
    this.server.on("error", (error) => {
      this.state = { ...this.state, status: "failed", errorCode: codeFromError(error, "local_ai_engine_failed"), updatedAt: new Date().toISOString() };
      this.saveState();
    });
    this.server.on("close", (code) => {
      if (this.state.status === "ready" || this.state.status === "starting") {
        this.state = { ...this.state, status: code === 0 ? "stopped" : "failed", errorCode: code === 0 ? null : "local_ai_engine_exit", updatedAt: new Date().toISOString() };
        this.saveState();
      }
      this.server = null;
    });
    try {
      await this.waitForHealth();
      return this.markReady();
    } catch (error) {
      this.stop();
      this.state = { ...this.state, status: "failed", errorCode: codeFromError(error, "local_ai_start_failed"), updatedAt: new Date().toISOString() };
      this.saveState();
      throw error;
    }
  }

  stop(): void {
    // Android build b10516 crashes in its SIGTERM handler. SIGKILL avoids a
    // native tombstone while the Host still closes its own server cleanly.
    if (this.server && !this.server.killed) this.server.kill("SIGKILL");
    this.server = null;
    if (this.state.status !== "not_installed") {
      this.state = { ...this.state, status: "stopped", updatedAt: new Date().toISOString() };
      this.saveState();
    }
  }

  private async waitForHealth(): Promise<void> {
    const deadline = Date.now() + 30_000;
    let lastError: unknown = null;
    while (Date.now() < deadline) {
      try {
        const response = await fetch(`http://127.0.0.1:${this.port}/health`, {
          headers: { Authorization: `Bearer ${this.apiKey}` },
          signal: AbortSignal.timeout(2_000),
        });
        if (response.ok) return;
        lastError = new Error(`local_ai_health_${response.status}`);
      } catch (error) {
        lastError = error;
      }
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    throw new Error(lastError instanceof Error ? "local_ai_health_timeout" : "local_ai_health_timeout");
  }

  private async probeHealth(): Promise<boolean> {
    try {
      const response = await fetch(`http://127.0.0.1:${this.port}/health`, {
        headers: { Authorization: `Bearer ${this.apiKey}` },
        signal: AbortSignal.timeout(750),
      });
      return response.ok;
    } catch {
      return false;
    }
  }

  private markReady(): LocalAiStatus {
    this.state = {
      ...this.state,
      status: "ready",
      modelFile: this.modelPath,
      bytesDownloaded: LOCAL_AI_MODEL.expectedBytes,
      totalBytes: LOCAL_AI_MODEL.expectedBytes,
      engineVersion: LOCAL_AI_ENGINE.version,
      piVersion: this.piVersion(),
      errorCode: null,
      updatedAt: new Date().toISOString(),
    };
    this.saveState();
    return this.status();
  }

  private async downloadModel(): Promise<LocalAiStatus> {
    const operationId = randomUUID();
    const existing = this.partialBytes();
    const available = availableBytes(this.root);
    if (available !== null && available < LOCAL_AI_MODEL.expectedBytes - existing + 100 * 1024 * 1024) {
      this.state = { ...this.state, status: "failed", operationId, errorCode: "insufficient_storage", updatedAt: new Date().toISOString() };
      this.saveState();
      throw new Error("insufficient_storage");
    }
    if (isReadyModel(this.modelPath)) {
      const digest = await this.digest(this.modelPath);
      if (digest === LOCAL_AI_MODEL.sha256) {
        this.state = { ...this.state, status: "stopped", modelFile: this.modelPath, bytesDownloaded: LOCAL_AI_MODEL.expectedBytes, operationId, errorCode: null, updatedAt: new Date().toISOString() };
        this.saveState();
        return this.status();
      }
      renameSync(this.modelPath, `${this.modelPath}.invalid.${Date.now()}`);
    }
    this.state = { ...this.state, status: "downloading", modelFile: null, bytesDownloaded: existing, totalBytes: LOCAL_AI_MODEL.expectedBytes, operationId, errorCode: null, updatedAt: new Date().toISOString() };
    this.saveState();
    this.log("local_ai.download_started", { operationId, modelId: LOCAL_AI_MODEL.id, bytesDownloaded: existing, totalBytes: LOCAL_AI_MODEL.expectedBytes });
    try {
      let response = await fetch(LOCAL_AI_MODEL.sourceUrl, {
        ...(existing > 0 ? { headers: { Range: `bytes=${existing}-` } } : {}),
        redirect: "follow",
        signal: AbortSignal.timeout(120_000),
      });
      let offset = existing;
      if (existing > 0 && response.status !== 206) {
        offset = 0;
        response = await fetch(LOCAL_AI_MODEL.sourceUrl, { redirect: "follow", signal: AbortSignal.timeout(120_000) });
      }
      if (!response.ok || !response.body) throw new Error("model_download_http_failed");
      const stream = createWriteStream(this.partialPath, { flags: offset > 0 ? "a" : "w", mode: 0o600 });
      const reader = response.body.getReader();
      let downloaded = offset;
      try {
        while (true) {
          const next = await reader.read();
          if (next.done) break;
          if (!next.value) continue;
          const chunk = Buffer.from(next.value);
          if (!stream.write(chunk)) await new Promise<void>((resolve) => stream.once("drain", resolve));
          downloaded += chunk.length;
          this.state = { ...this.state, bytesDownloaded: downloaded, updatedAt: new Date().toISOString() };
          this.saveState();
        }
      } finally {
        stream.end();
        await new Promise<void>((resolve, reject) => { stream.once("close", resolve); stream.once("error", reject); });
      }
      if (downloaded !== LOCAL_AI_MODEL.expectedBytes) throw new Error("model_size_mismatch");
      this.state = { ...this.state, status: "verifying", bytesDownloaded: downloaded, updatedAt: new Date().toISOString() };
      this.saveState();
      const digest = await this.digest(this.partialPath);
      if (digest !== LOCAL_AI_MODEL.sha256) throw new Error("model_checksum_mismatch");
      renameSync(this.partialPath, this.modelPath);
      this.state = { ...this.state, status: "stopped", modelFile: this.modelPath, operationId: null, errorCode: null, updatedAt: new Date().toISOString() };
      this.saveState();
      this.log("local_ai.download_completed", { operationId, modelId: LOCAL_AI_MODEL.id, bytesDownloaded: downloaded, totalBytes: downloaded });
      return this.status();
    } catch (error) {
      this.state = { ...this.state, status: "failed", operationId, errorCode: codeFromError(error, "model_download_failed"), updatedAt: new Date().toISOString() };
      this.saveState();
      this.log("local_ai.download_failed", { operationId, modelId: LOCAL_AI_MODEL.id, bytesDownloaded: this.state.bytesDownloaded, errorCode: this.state.errorCode });
      throw error;
    }
  }

  private partialBytes(): number {
    try { return Math.min(LOCAL_AI_MODEL.expectedBytes, statSync(this.partialPath).size); } catch { return 0; }
  }

  private async digest(path: string): Promise<string> {
    const hash = createHash("sha256");
    const stream = (await import("node:fs")).createReadStream(path);
    for await (const chunk of stream) hash.update(chunk as Buffer);
    return hash.digest("hex");
  }

  private saveState(): void {
    this.state.updatedAt = new Date().toISOString();
    persistJson(this.statePath, this.state);
  }

  private piInstalled(): boolean {
    const result = spawnSync("pi", ["--version"], { stdio: "ignore", timeout: 5_000 });
    return result.status === 0;
  }

  private piVersion(): string | null {
    const output = safeReadJson<{ version?: string }>(join(this.root, "pi-version.json"));
    return output?.version ?? (this.piInstalled() ? LOCAL_AI_PI.version : null);
  }
}
