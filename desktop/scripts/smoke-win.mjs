import { access, mkdtemp, readFile, rm } from "node:fs/promises";
import { constants } from "node:fs";
import { spawn } from "node:child_process";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

if (process.platform !== "win32") {
  throw new Error("The packaged Electron smoke test must run on Windows");
}

const desktopRoot = resolve(fileURLToPath(new URL("../", import.meta.url)));
const executable = resolve(process.argv[2] || join(desktopRoot, "release", "win-unpacked", "Study Sprint PC.exe"));
const smokeRoot = await mkdtemp(join(tmpdir(), "study-sprint-desktop-smoke-"));
const userData = join(smokeRoot, "user-data");
let output = "";

function runExecutable() {
  return new Promise((resolveProcess, rejectProcess) => {
    const child = spawn(executable, ["--smoke-test", `--user-data-dir=${userData}`, "--disable-gpu"], {
      cwd: dirname(executable),
      stdio: ["ignore", "pipe", "pipe"],
      shell: false,
      windowsHide: true
    });
    const timeout = setTimeout(() => {
      child.kill();
      rejectProcess(new Error("Packaged Electron smoke test timed out"));
    }, 120_000);
    child.stdout.on("data", chunk => { output += chunk.toString(); });
    child.stderr.on("data", chunk => { output += chunk.toString(); });
    child.once("error", error => {
      clearTimeout(timeout);
      rejectProcess(error);
    });
    child.once("close", (code, signal) => {
      clearTimeout(timeout);
      resolveProcess({ code, signal });
    });
  });
}

try {
  await access(executable, constants.F_OK);
  const result = await runExecutable();
  if (result.code !== 0) {
    throw new Error(`Packaged Electron smoke test failed with code ${result.code ?? "unknown"}${result.signal ? ` (${result.signal})` : ""}\n${output}`);
  }

  const backup = join(userData, "smoke-downloads", "study-sprint-pc-5.1.0-backup.json");
  const payload = JSON.parse(await readFile(backup, "utf8"));
  if (payload?.state?.profile?.name !== "Smoke Student" || payload?.state?.profile?.onboarded !== true) {
    throw new Error("Smoke test backup did not contain the completed guest profile");
  }
  console.log(`Windows Electron smoke test passed: ${backup}`);
} finally {
  await rm(smokeRoot, { recursive: true, force: true });
}
