import { ChatError } from './chat.mjs';
import { canonicalMcqs, parseMcqs } from './mcq.mjs';

export const SEARCH_MODEL = 'tavily/search';
export const configured = key => typeof key === 'string' && /^tvly-[A-Za-z0-9_-]{16,256}$/.test(key);
const exams = {
  CET: { name: 'MHT-CET', marker: /MHT[\s-]*CET|Maharashtra.*CET/i, excluded: /\bNEET\b|\bJEE\b|\bGATE\b|\bUPSC\b|\bSAT\b/i },
  JEE: { name: 'JEE Main', marker: /\bJEE\b|Joint Entrance/i, excluded: /\bNEET\b|MHT[\s-]*CET|\bGATE\b|\bUPSC\b|\bSAT\b/i },
  NEET: { name: 'NEET UG', marker: /\bNEET\b/i, excluded: /\bJEE\b|MHT[\s-]*CET|\bGATE\b|\bUPSC\b|\bSAT\b/i }
};
// Only accept complete source question blocks. Search snippets alone cannot prove an answer.
function sourcedQuestions(content) {
  const text = content.replace(/\r/g, '\n').replace(/<[^>]*>/g, '\n');
  const blocks = text.match(/^(?:Q(?:uestion)?\s*)?\d{1,2}[.)]\s+[^\n]+\n(?:[^\n]*\n){0,12}?\s*Answer:\s*[A-D]\s*$/gim) ?? [];
  return blocks.flatMap(block => {
    try {
      const numbered = block.replace(/^(?:Q(?:uestion)?\s*)?\d{1,2}[.)]/i, '1.');
      return parseMcqs(numbered, 1);
    } catch { return []; }
  });
}
export async function searchMcqs(input, key, fetcher = fetch) {
  if (!configured(key)) throw new ChatError('failed-precondition', 'Online MCQ search needs TAVILY_API_KEY in Render.');
  const exam = exams[input.exam];
  if (!exam) throw new ChatError('invalid-argument', 'Choose MHT-CET, JEE Main or NEET UG in your profile.');
  let response;
  try {
    response = await fetcher('https://api.tavily.com/search', {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(45000),
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      body: JSON.stringify({ query: `${exam.name} ${input.messages.at(-1).content.slice(0, 300)} MCQ four options correct answer`, search_depth: 'basic', max_results: 5, include_answer: false, include_raw_content: 'text', include_images: false, safe_search: true })
    });
    if (!response.ok) {
      await response.body?.cancel().catch(() => {});
      if ([429,432,433].includes(response.status)) throw new ChatError('resource-exhausted', 'Online search allowance is reached. Try again later.');
      if ([401,403].includes(response.status)) throw new ChatError('failed-precondition', 'Tavily search key needs attention from the app owner.');
      throw new ChatError('unavailable', 'Online search could not respond. Retry later.', true);
    }
    const reader = response.body.getReader(); const chunks = []; let bytes = 0;
    try {
      while (true) {
        const next = await reader.read(); if (next.done) break;
        bytes += next.value.byteLength;
        if (bytes > 1024 * 1024) { await reader.cancel(); throw Error('large'); }
        chunks.push(Buffer.from(next.value));
      }
    } finally { reader.releaseLock(); }
    const data = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    const questions = []; const sources = []; const seen = new Set();
    for (const result of Array.isArray(data.results) ? data.results.slice(0, 5) : []) {
      const title = result?.title;
      if (typeof title !== 'string' || !exam.marker.test(title) || exam.excluded.test(title)) continue;
      let url;
      try { url = new URL(result.url); if (url.protocol !== 'https:' || url.username || url.password) continue; } catch { continue; }
      const content = typeof result.raw_content === 'string' ? result.raw_content : result.content;
      if (typeof content !== 'string') continue;
      let used = false;
      for (const question of sourcedQuestions(content.slice(0, 40000))) {
        const id = question.question.toLowerCase();
        if (seen.has(id)) continue;
        seen.add(id); questions.push(question); used = true;
        if (questions.length >= 5) break;
      }
      if (used) sources.push({ title: title.slice(0, 160), url: url.href });
      if (questions.length >= 5) break;
    }
    if (!questions.length) throw new ChatError('unavailable', 'No complete four-option MCQs with answers were found in exam sources. Try another topic.');
    return { answer: canonicalMcqs(questions), model: SEARCH_MODEL, quiz: true, sources, suggestions: '' };
  } catch (error) {
    if (error instanceof ChatError) throw error;
    throw new ChatError('unavailable', 'Online search could not return a complete answer. Retry later.', true);
  }
}
