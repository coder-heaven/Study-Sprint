import { cp, readFile, rm, writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const desktopRoot = resolve(fileURLToPath(new URL("../", import.meta.url)));
const repositoryRoot = resolve(desktopRoot, "..");
const pcRoot = join(repositoryRoot, "pc");
const stagedPwa = join(desktopRoot, "pc-dist");
const pcBuildScript = join(pcRoot, "scripts", "build.mjs");
const mathJaxPackage = join(desktopRoot, "node_modules", "mathjax");

function runNodeScript(script, args = []) {
  return new Promise((resolveProcess, rejectProcess) => {
    const child = spawn(process.execPath, [script, ...args], {
      cwd: pcRoot,
      stdio: "inherit",
      shell: false,
      windowsHide: true
    });
    child.on("error", rejectProcess);
    child.on("exit", code => {
      if (code === 0) resolveProcess();
      else rejectProcess(new Error(`${script} exited with code ${code}`));
    });
  });
}

await runNodeScript(pcBuildScript);
await rm(stagedPwa, { recursive: true, force: true });
await cp(join(pcRoot, "dist"), stagedPwa, { recursive: true });

const stagedIndex = join(stagedPwa, "index.html");
let indexHtml = await readFile(stagedIndex, "utf8");

const remoteMathJaxTag = '<script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js" async></script>';
const localMathJaxTag = '<script src="mathjax/tex-mml-chtml.js" async></script>';
if (!indexHtml.includes(remoteMathJaxTag)) throw new Error("Expected the PC MathJax CDN tag while staging");
indexHtml = indexHtml.replace(remoteMathJaxTag, localMathJaxTag);
await writeFile(stagedIndex, indexHtml);

const stagedMathJax = join(stagedPwa, "mathjax");
await cp(join(mathJaxPackage, "es5"), stagedMathJax, { recursive: true });
await cp(join(mathJaxPackage, "LICENSE"), join(stagedMathJax, "LICENSE"));
console.log(`Staged the existing PC PWA in ${stagedPwa}`);
