const { contextBridge } = require("electron");

const pageErrors = [];
const rememberPageError = message => {
  if (pageErrors.length < 25) pageErrors.push(String(message));
};
window.addEventListener("error", event => rememberPageError(event.error?.stack || event.message || "Uncaught renderer error"));
window.addEventListener("unhandledrejection", event => rememberPageError(event.reason?.stack || event.reason || "Unhandled renderer rejection"));

contextBridge.exposeInMainWorld("studySprintDesktop", Object.freeze({
  isDesktop: true,
  getPageErrors: () => [...pageErrors]
}));
