import http from "node:http";
import { readFile } from "node:fs/promises";
import { extname, normalize, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));
const port = Number(process.env.PORT || 4173);
const types = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".mjs": "text/javascript", ".json": "application/json", ".webmanifest": "application/manifest+json", ".svg": "image/svg+xml" };
const server = http.createServer(async (request, response) => {
  const requested = request.url?.split("?")[0] || "/";
  const relative = requested === "/" ? "index.html" : requested.replace(/^\/+/, "");
  const file = normalize(join(root, relative));
  if (!file.startsWith(root)) { response.writeHead(403); return response.end("Forbidden"); }
  try {
    const body = await readFile(file);
    response.writeHead(200, { "Content-Type": types[extname(file)] || "application/octet-stream", "Cache-Control": "no-cache" });
    response.end(body);
  } catch {
    response.writeHead(404); response.end("Not found");
  }
});
server.listen(port, "127.0.0.1", () => console.log(`Study Sprint PC: http://127.0.0.1:${port}`));
