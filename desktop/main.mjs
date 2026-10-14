import { app, BrowserWindow, protocol, session } from "electron";
import { mkdir, readFile, stat } from "node:fs/promises";
import { basename, extname, isAbsolute, join, normalize, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const APP_SCHEME = "study-sprint";
const APP_HOST = "bundle";
const APP_ORIGIN = `${APP_SCHEME}://${APP_HOST}`;
const IS_SMOKE_TEST = process.argv.includes("--smoke-test");
const SMOKE_DOWNLOAD_TIMEOUT_MS = 15_000;

let smokeDownloadPromise;

protocol.registerSchemesAsPrivileged([
  {
    scheme: APP_SCHEME,
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: false,
      stream: true
    }
  }
]);

const MIME_TYPES = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".svg": "image/svg+xml",
  ".webmanifest": "application/manifest+json",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf"
};

const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "base-uri 'none'",
  "object-src 'none'",
  "frame-ancestors 'none'",
  "form-action 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "worker-src 'self'"
].join("; ");

function isAppUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === `${APP_SCHEME}:` &&
      url.hostname === APP_HOST &&
      url.port === "" &&
      url.username === "" &&
      url.password === "";
  } catch {
    return false;
  }
}

function response(status, body, contentType = "text/plain; charset=utf-8") {
  return new Response(body, {
    status,
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": contentType
    }
  });
}

function staticRoot() {
  return resolve(join(app.getAppPath(), "pc-dist"));
}

function requestedFile(requestUrl) {
  const url = new URL(requestUrl);
  if (!isAppUrl(requestUrl)) return null;

  let pathname;
  try {
    pathname = decodeURIComponent(url.pathname);
  } catch {
    return null;
  }
  if (pathname.includes("\0")) return null;

  const root = staticRoot();
  const requested = pathname === "/" || pathname === "" ? "index.html" : pathname.replace(/^[/\\]+/, "");
  const candidate = resolve(root, normalize(requested));
  const candidateRelativeToRoot = relative(root, candidate);
  if (candidateRelativeToRoot === "" ||
      isAbsolute(candidateRelativeToRoot) ||
      candidateRelativeToRoot === ".." ||
      candidateRelativeToRoot.startsWith(`..${sep}`)) return null;
  return candidate;
}

async function handleAppRequest(request) {
  if (request.method !== "GET" && request.method !== "HEAD") return response(405, "Method not allowed");
  const file = requestedFile(request.url);
  if (!file) return response(404, "Not found");
  if (IS_SMOKE_TEST) console.log(`Smoke request ${request.method} ${request.url} -> ${file}`);

  try {
    const fileInfo = await stat(file);
    if (!fileInfo.isFile()) return response(404, "Not found");
    const body = await readFile(file);
    const headers = {
      "Cache-Control": "no-store",
      "Content-Type": MIME_TYPES[extname(file).toLowerCase()] || "application/octet-stream",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer"
    };
    if (extname(file).toLowerCase() === ".html") headers["Content-Security-Policy"] = CONTENT_SECURITY_POLICY;
    return new Response(request.method === "HEAD" ? null : body, { headers });
  } catch {
    return response(404, "Not found");
  }
}

function navigationUrl(detailsOrUrl) {
  return typeof detailsOrUrl === "string" ? detailsOrUrl : detailsOrUrl?.url;
}

function blockExternalNavigation(event, detailsOrUrl) {
  if (!isAppUrl(navigationUrl(detailsOrUrl))) event.preventDefault();
}

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: "#0b1411",
    webPreferences: {
      allowRunningInsecureContent: false,
      contextIsolation: true,
      devTools: !app.isPackaged,
      nodeIntegration: false,
      preload: fileURLToPath(new URL("./preload.cjs", import.meta.url)),
      sandbox: true,
      webSecurity: true,
      webviewTag: false,
      navigateOnDragDrop: false,
      spellcheck: false
    }
  });

  mainWindow.webContents.on("will-navigate", blockExternalNavigation);
  mainWindow.webContents.on("will-frame-navigate", blockExternalNavigation);
  mainWindow.webContents.on("will-redirect", blockExternalNavigation);
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-attach-webview", event => event.preventDefault());

  const consoleErrors = [];
  mainWindow.webContents.on("console-message", (_event, details, legacyMessage, legacyLine, legacySource) => {
    const level = typeof details === "object" ? details?.level : details;
    if (level === "error" || level === 3) {
      const message = typeof details === "object" ? details.message : String(legacyMessage ?? details);
      const line = typeof details === "object" ? details.lineNumber : legacyLine;
      const source = typeof details === "object" ? details.sourceId : legacySource;
      consoleErrors.push(`${source || "renderer"}:${line || "?"} ${message}`);
    }
  });
  mainWindow.webContents.on("preload-error", (_event, preloadPath, error) => {
    consoleErrors.push(`Preload error in ${preloadPath}: ${error.message}`);
  });
  mainWindow.webContents.on("render-process-gone", (_event, details) => {
    consoleErrors.push(`Renderer process gone: ${details.reason}`);
  });

  return { mainWindow, consoleErrors, loadPromise: mainWindow.loadURL(`${APP_ORIGIN}/index.html`) };
}

async function configureSession() {
  const appSession = session.defaultSession;
  appSession.setPermissionCheckHandler(() => false);
  appSession.setPermissionRequestHandler((_webContents, _permission, callback) => callback(false));
  appSession.setDevicePermissionHandler?.(() => false);

  if (!IS_SMOKE_TEST) return;
  const smokeDownloads = join(app.getPath("userData"), "smoke-downloads");
  await mkdir(smokeDownloads, { recursive: true });
  app.setPath("downloads", smokeDownloads);
  appSession.setDownloadPath(smokeDownloads);
  smokeDownloadPromise = new Promise((resolveDownload, rejectDownload) => {
    appSession.on("will-download", (_event, item) => {
      const fileName = basename(item.getFilename());
      const target = join(smokeDownloads, fileName);
      item.setSavePath(target);
      if (!fileName.toLowerCase().endsWith(".json")) return;
      item.once("done", (_doneEvent, state) => {
        if (state === "completed") resolveDownload(target);
        else rejectDownload(new Error(`Backup download ended with state ${state}`));
      });
    });
  });
}

function withTimeout(promise, timeoutMs, message) {
  let timeout;
  const timeoutPromise = new Promise((_resolve, reject) => {
    timeout = setTimeout(() => reject(new Error(message)), timeoutMs);
  });
  return Promise.race([promise, timeoutPromise]).finally(() => clearTimeout(timeout));
}

async function runSmokeTest(mainWindow, consoleErrors) {
  const smokeResult = await mainWindow.webContents.executeJavaScript(`(async () => {
    const waitForPaint = () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    const visible = element => {
      if (!element || element.hidden || element.classList.contains("hidden")) return false;
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.display !== "none" && style.visibility !== "hidden" && rect.width > 0 && rect.height > 0;
    };
    const hiddenAncestors = element => {
      const hidden = [];
      for (let node = element; node; node = node.parentElement) {
        if (node.classList.contains("hidden") || getComputedStyle(node).display === "none") hidden.push(node.id || node.className || node.tagName);
      }
      return hidden;
    };
    const welcome = document.querySelector("#welcome-screen");
    if (!visible(welcome) || hiddenAncestors(welcome).length) {
      throw new Error("Fresh onboarding is not visible: " + hiddenAncestors(welcome).join(", "));
    }
     const nodeDisabled = typeof require === "undefined" && typeof module === "undefined" &&
       typeof window.require === "undefined" &&
       !(typeof process !== "undefined" && typeof process.binding === "function");
     if (!nodeDisabled) throw new Error("Renderer unexpectedly exposes Node integration");

    document.querySelector("#welcome-name").value = "Smoke Student";
    document.querySelector("#welcome-exam").value = "CET";
    document.querySelector("#welcome-grade").value = "11";
    document.querySelector("#welcome-terms").checked = true;
     document.querySelector("#welcome-form").dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await waitForPaint();
    if (!visible(document.querySelector(".app-shell")) || visible(welcome)) throw new Error("Guest onboarding did not complete");

    const navigate = async view => {
      const button = document.querySelector("[data-view=\"" + view + "\"]");
      if (!button) throw new Error("Missing " + view + " navigation button");
      button.click();
      await waitForPaint();
      if (!document.querySelector("#view-" + view).classList.contains("active-view")) throw new Error("Could not navigate to " + view);
    };
    await navigate("focus");
    await navigate("progress");
    await navigate("settings");
    document.querySelector('[data-action="export"]').click();
    await new Promise(resolve => setTimeout(resolve, 100));

    return {
       nodeDisabled,
       pageErrors: window.studySprintDesktop && typeof window.studySprintDesktop.getPageErrors === "function"
         ? window.studySprintDesktop.getPageErrors() : []
    };
  })()`, true);

  if (!smokeResult.nodeDisabled) throw new Error("Smoke test detected Node in the renderer");
  if (smokeResult.pageErrors.length) throw new Error(`Renderer page errors:\n${smokeResult.pageErrors.join("\n")}`);
  if (consoleErrors.length) throw new Error(`Renderer console errors:\n${consoleErrors.join("\n")}`);

  const backup = await withTimeout(smokeDownloadPromise, SMOKE_DOWNLOAD_TIMEOUT_MS, "Timed out waiting for the JSON backup download");
  const backupPayload = JSON.parse(await readFile(backup, "utf8"));
  if (backupPayload?.state?.profile?.name !== "Smoke Student" || backupPayload?.state?.profile?.onboarded !== true) {
    throw new Error("Downloaded JSON backup did not contain the completed guest profile");
  }
  console.log(`Smoke flow passed; downloaded ${backup}`);
}

app.whenReady().then(async () => {
  await configureSession();
  protocol.handle(APP_SCHEME, handleAppRequest);
  const { mainWindow, consoleErrors, loadPromise } = createWindow();
  if (IS_SMOKE_TEST) {
    try {
      await loadPromise;
      await runSmokeTest(mainWindow, consoleErrors);
      app.exit(0);
     } catch (error) {
       console.error(error);
       if (consoleErrors.length) console.error(`Renderer diagnostics:\n${consoleErrors.join("\n")}`);
      app.exit(1);
    }
    return;
  }
  loadPromise.catch(error => console.error("Could not load bundled PC app", error));
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
}).catch(error => {
  console.error("Could not start Study Sprint PC", error);
  if (IS_SMOKE_TEST) app.exit(1);
  else app.quit();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
