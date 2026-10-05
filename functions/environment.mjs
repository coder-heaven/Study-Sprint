import { configured } from './gemini.mjs';

// Google documents both names. Never log or return environment values.
export function googleKey(environment = process.env) {
  for (const name of ['GEMINI_API_KEY', 'GOOGLE_API_KEY']) {
    const key = environment[name]?.trim();
    if (configured(key)) return key;
  }
  return undefined;
}
