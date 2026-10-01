import { copyFileSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const hostRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(hostRoot, "dist", "index.js");
const target = join(hostRoot, "..", "app", "src", "main", "assets", "host.mjs");

copyFileSync(source, target);
copyFileSync(join(hostRoot, "dist", "generatedSkills.js"), join(hostRoot, "..", "app", "src", "main", "assets", "generatedSkills.js"));
copyFileSync(join(hostRoot, "dist", "skillWorkshop.js"), join(hostRoot, "..", "app", "src", "main", "assets", "skillWorkshop.js"));
copyFileSync(join(hostRoot, "dist", "localAi.js"), join(hostRoot, "..", "app", "src", "main", "assets", "localAi.js"));
copyFileSync(join(hostRoot, "dist", "piRuntime.js"), join(hostRoot, "..", "app", "src", "main", "assets", "piRuntime.js"));
const packageJson = JSON.parse(readFileSync(join(hostRoot, "package.json"), "utf8"));
process.stdout.write(`synced_host_asset version=${packageJson.version}\n`);

copyFileSync(join(hostRoot, "dist", "locale.js"), join(hostRoot, "..", "app", "src", "main", "assets", "locale.js"));
copyFileSync(join(hostRoot, "dist", "themePackages.js"), join(hostRoot, "..", "app", "src", "main", "assets", "themePackages.js"));
