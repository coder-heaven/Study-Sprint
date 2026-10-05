import { ChatError } from './chat.mjs';
import { canonicalMcqs, parseMcqs } from './mcq.mjs';

export const SEARCH_MODEL = 'tavily/search';
export const configured = key => typeof key === 'string' && /^tvly-[A-Za-z0-9_-]{16,256}$/.test(key);
const sharedSources = ['examside.com', 'selfstudys.com', 'prepizo.com'];
const exams = {
  CET: { name: 'MHT-CET', marker: /MHT[\s-]*CET|Maharashtra.*CET/i, excluded: /\bNEET\b|\bJEE\b|\bGATE\b|\bUPSC\b|\bSAT\b/i, domains: sharedSources, examside: '/past-years/jee/mht-cet' },
  JEE: { name: 'JEE Main', marker: /\bJEE\b|Joint Entrance/i, excluded: /\bNEET\b|MHT[\s-]*CET|\bGATE\b|\bUPSC\b|\bSAT\b/i, domains: [...sharedSources, 'examgoal.com'], examside: '/past-years/jee/jee-main' },
  NEET: { name: 'NEET UG', marker: /\bNEET\b/i, excluded: /\bJEE\b|MHT[\s-]*CET|\bGATE\b|\bUPSC\b|\bSAT\b/i, domains: [...sharedSources, 'examgoal.com'], examside: '/past-years/medical/neet' }
};
const allowedHost = (host, domains) => domains.some(domain => host === domain || host.endsWith(`.${domain}`));
export function studyTopic(prompt) {
  const first = prompt.split(/\bDifficulty\s*:/i)[0].trim();
  return first.replace(/^(?:(?:please\s+)?(?:give|create|generate|make|find|show)\s+me\s+)?(?:\d+|some|a few)?\s*(?:original\s+)?(?:mcqs?|questions?)?\s*(?:based\s+on|about|on|for)?\s*/i, '')
    .replace(/\s+for\s+practice\s*$/i, '').trim().slice(0, 160) || 'selected exam study concepts';
}
export function topicMatches(topic, title, url, snippet) {
  if (/\b(?:some\s+)?basic\s+concepts?\s+of\s+chemistry\b/i.test(topic)) {
    return /\b(?:basic concepts of chemistry|some basic concepts of chemistry|mole concept|moles|molar mass|stoichiometry|avogadro|empirical formula)\b/i.test(`${title} ${url}`) &&
      !/\b(?:redox|oxidation|reduction|mba|management)\b/i.test(`${title} ${url}`);
  }
  const tokens = topic.toLowerCase().match(/[a-z]{4,}/g)?.filter(token => !['some','about','practice','questions','question','concept','concepts','chemistry','physics','mathematics','medium','standard','steps'].includes(token)) ?? [];
  if (!tokens.length) return true;
  return tokens.some(token => `${title} ${url} ${snippet}`.toLowerCase().includes(token));
}
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
  const topic = studyTopic(input.messages.at(-1).content);
  let response;
  try {
    response = await fetcher('https://api.tavily.com/search', {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(45000),
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      body: JSON.stringify({ query: `${exam.name} ${topic} MCQ four options correct answer`, search_depth: 'basic', max_results: 5, include_domains: exam.domains, include_domains_mode: 'restrict', include_answer: false, include_raw_content: 'text', include_images: false, safe_search: true })
    });
    if (!response.ok) {
      await response.body?.cancel().catch(() => {});
      if ([429,432,433].includes(response.status)) throw new ChatError('resource-exhausted', 'Online search allowance is reached. Try again later.', true);
      if ([401,403].includes(response.status)) throw new ChatError('failed-precondition', 'Tavily search key needs attention from the app owner.', true);
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
    const questions = []; const sources = []; const context = []; const seen = new Set();
    for (const result of Array.isArray(data.results) ? data.results.slice(0, 5) : []) {
      const title = result?.title;
      if (typeof title !== 'string' || !exam.marker.test(title) || exam.excluded.test(title)) continue;
      let url;
      try {
        url = new URL(result.url);
        if (url.protocol !== 'https:' || url.username || url.password || !allowedHost(url.hostname, exam.domains)) continue;
        // ExamSIDE groups MHT-CET under /jee; require the actual exam segment, not its parent group.
        if (allowedHost(url.hostname, ['examside.com']) &&
            !(url.pathname === exam.examside || url.pathname.startsWith(`${exam.examside}/`))) continue;
      } catch { continue; }
      const content = typeof result.raw_content === 'string' ? result.raw_content : result.content;
      if (typeof content !== 'string' || !topicMatches(topic, title, url.pathname, result.content ?? '')) continue;
      // Use only bounded search snippets as untrusted study context, not full page navigation or instructions.
      context.push(`${title.slice(0, 160)}: ${String(result.content ?? '').slice(0, 1200)}`);
      let used = false;
      for (const question of sourcedQuestions(content.slice(0, 40000))) {
        const id = question.question.toLowerCase();
        if (seen.has(id)) continue;
        seen.add(id); questions.push(question); used = true;
        if (questions.length >= 5) break;
      }
      if (used || context.length <= 4) sources.push({ title: title.slice(0, 160), url: url.href });
      if (questions.length >= 5) break;
    }
    // A search can return only chapter indexes, unrelated exams or no results at all.
    // Give the tutor no unverified source material, but still allow original exam practice.
    if (!questions.length) return { context, sources: sources.slice(0, 4) };
    return { answer: canonicalMcqs(questions), model: SEARCH_MODEL, quiz: true, sources: sources.slice(0, 5), suggestions: '' };
  } catch (error) {
    if (error instanceof ChatError) throw error;
    throw new ChatError('unavailable', 'Online search could not return a complete answer. Retry later.', true);
  }
}
