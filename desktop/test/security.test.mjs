import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const desktopRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const mainSource = await readFile(join(desktopRoot, "main.mjs"), "utf8");
const preloadSource = await readFile(join(desktopRoot, "preload.cjs"), "utf8");
const buildSource = await readFile(join(desktopRoot, "scripts", "build-pc.mjs"), "utf8");

test("desktop renderer keeps Node disabled and isolated", () => {
  assert.match(mainSource, /nodeIntegration:\s*false/);
  assert.match(mainSource, /contextIsolation:\s*true/);
  assert.match(mainSource, /sandbox:\s*true/);
  assert.match(mainSource, /webSecurity:\s*true/);
  assert.match(mainSource, /webviewTag:\s*false/);
  assert.match(preloadSource, /contextBridge\.exposeInMainWorld/);
  assert.doesNotMatch(preloadSource, /ipcRenderer|node:fs|node:child_process/);
});

test("desktop renderer restricts navigation, windows, frames, and permissions", () => {
  assert.match(mainSource, /will-navigate/);
  assert.match(mainSource, /will-frame-navigate/);
  assert.match(mainSource, /will-redirect/);
  assert.match(mainSource, /setWindowOpenHandler\(\(\) => \(\{ action: "deny" \}\)\)/);
  assert.match(mainSource, /will-attach-webview/);
  assert.match(mainSource, /setPermissionCheckHandler\(\(\) => false\)/);
  assert.match(mainSource, /setPermissionRequestHandler/);
  assert.match(mainSource, /callback\(false\)/);
});

test("desktop CSP and staging build do not execute remote scripts", () => {
  assert.match(mainSource, /script-src 'self'/);
  assert.match(mainSource, /connect-src 'self'/);
  assert.doesNotMatch(mainSource, /cdn\.jsdelivr\.net/);
  assert.match(buildSource, /process\.execPath/);
  assert.doesNotMatch(buildSource, /npm\.cmd|npmCommand/);
  assert.doesNotMatch(buildSource, /shell:\s*true/);
});
