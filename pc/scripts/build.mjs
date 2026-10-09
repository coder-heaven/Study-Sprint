import { cp, mkdir, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { sync } from "./sync.mjs";

await sync({ check: true });
const pc = new URL("../", import.meta.url);
const dist = new URL("../dist/", import.meta.url);
await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
for (const entry of ["index.html", "styles.css", "app.js", "core.mjs", "data.mjs", "manifest.webmanifest", "icon.svg", "sw.js"]) {
  await cp(new URL(`../${entry}`, import.meta.url), new URL(entry, dist));
}
console.log(`Built Study Sprint PC ${await sync({ check: true })} in ${fileURLToPath(dist)}`);
