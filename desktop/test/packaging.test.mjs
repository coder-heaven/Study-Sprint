import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { constants } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const desktopRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const stagedRoot = join(desktopRoot, "pc-dist");
const packageJson = JSON.parse(await readFile(join(desktopRoot, "package.json"), "utf8"));
const lockJson = JSON.parse(await readFile(join(desktopRoot, "package-lock.json"), "utf8"));

test("desktop pins a supported Electron and reproducible packaging tools", () => {
  assert.equal(packageJson.devDependencies.electron, "44.7.2");
  assert.equal(lockJson.packages["node_modules/electron"].version, "44.7.2");
  assert.equal(packageJson.devDependencies["electron-builder"], "26.15.3");
  assert.equal(packageJson.devDependencies.mathjax, "3.2.2");
});

test("staged desktop bundle contains local MathJax and its license", async () => {
  const indexHtml = await readFile(join(stagedRoot, "index.html"), "utf8");
  assert.match(indexHtml, /mathjax\/tex-mml-chtml\.js/);
  assert.doesNotMatch(indexHtml, /https:\/\/cdn\.jsdelivr\.net/);
  await access(join(stagedRoot, "mathjax", "tex-mml-chtml.js"), constants.F_OK);
  await access(join(stagedRoot, "mathjax", "LICENSE"), constants.F_OK);
});

test("staged fresh onboarding is outside the hidden app shell", async () => {
  const indexHtml = await readFile(join(stagedRoot, "index.html"), "utf8");
  assert.match(indexHtml, /<div id="welcome-screen"[\s\S]*?<div class="app-shell hidden">/);
  assert.doesNotMatch(indexHtml, /<div class="app-shell[^\"]*">\s*<div id="welcome-screen"/);
});

test("Windows packaging keeps both x64 targets and an asar bundle", async () => {
  const builderConfig = await readFile(join(desktopRoot, "electron-builder.yml"), "utf8");
  assert.match(builderConfig, /^asar:\s*true$/m);
  assert.match(builderConfig, /target:\s*nsis/);
  assert.match(builderConfig, /target:\s*portable/);
  assert.match(builderConfig, /- x64/);
  assert.match(builderConfig, /pc-dist\/\*\*\//);
});
