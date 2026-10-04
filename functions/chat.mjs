export const NEMOTRON = 'nvidia/nemotron-3-ultra-550b-a55b:free';
export const KIMI = 'moonshotai/kimi-k3';
export const GPT = 'openai/gpt-oss-20b';
export const GLM = 'z-ai/glm-5.3';
export const MODELS = [NEMOTRON, KIMI, GPT, GLM];
export class ChatError extends Error {
  constructor(code, message, retryable = false) { super(message); this.code = code; this.retryable = retryable; }
}
const bad = message => new ChatError('invalid-argument', message);
export function validate(data) {
  if (!data || !Array.isArray(data.messages) || data.messages.length < 1 || data.messages.length > 20) throw bad('Send between 1 and 20 conversation messages.');
  let total = 0;
  const messages = data.messages.map(m => {
    if (!m || !['user', 'assistant'].includes(m.role) || typeof m.content !== 'string') throw bad('Invalid conversation.');
    const content = m.content.trim();
    if (!content || content.length > (m.role === 'user' ? 4000 : 16000)) throw bad('A message is too long or empty.');
    total += content.length;
    return { role: m.role, content };
  });
  if (total > 24000) throw bad('This conversation is too long. Start a new chat.');
  if (messages[0].role !== 'user' || messages.at(-1).role !== 'user') throw bad('The conversation must begin and end with your question.');
  const photos = data.photos ?? [];
  if (!Array.isArray(photos) || photos.length > 4) throw bad('Attach a maximum of 4 photos per question.');
  for (const photo of photos) {
    if (typeof photo !== 'string' || photo.length > 350000 || !/^data:image\/jpeg;base64,[A-Za-z0-9+/]+={0,2}$/.test(photo)) throw bad('Invalid photo. Choose a smaller JPEG image.');
    const bytes = Buffer.from(photo.slice('data:image/jpeg;base64,'.length), 'base64');
    if (bytes.length < 4 || bytes[0] !== 255 || bytes[1] !== 216 || bytes[2] !== 255 || bytes.at(-2) !== 255 || bytes.at(-1) !== 217) throw bad('The attachment must be a JPEG photo.');
  }
  const model = data.model ?? 'auto';
  if (model !== 'auto' && !MODELS.includes(model)) throw bad('Choose a supported study model.');
  const exam = data.exam ?? 'CET'; const grade = data.grade ?? '11';
  if (!['CET', 'JEE', 'NEET'].includes(exam) || !['11', '12'].includes(grade)) throw bad('Choose CET, JEE or NEET and Class 11 or 12.');
  return { messages, photos, model, exam, grade };
}
export function tutorPrompt(input) {
  const exam = ['CET', 'JEE', 'NEET'].includes(input.exam) ? input.exam : 'CET';
  const guidance = {
    CET: 'MHT-CET: favour concise MCQ solutions, formula recall, units and speed. Explain Maharashtra Class 11/12 concepts and show valid option-elimination methods.',
    JEE: 'JEE: favour conceptual reasoning and multi-step physics, chemistry and mathematics solutions. Explain assumptions and show valid alternative methods; ask Main or Advanced when the distinction matters.',
    NEET: 'NEET: favour NCERT-aligned biology, chemistry and physics concepts, precise terminology and MCQ elimination. Explain why tempting distractors are wrong.'
  }[exam];
  return `You are Study Sprint's study tutor. The student chose ${exam}, Class ${input.grade === '12' ? '12' : '11'}. ${guidance} Answer the actual question and adapt its depth to this exam, without inventing official past-paper status or current syllabus facts. Explain clearly and give concise worked solutions. Occasionally, when relevant, add one short Exam tip (a valid shortcut, recall trick or timing strategy); do not repeat the same tip or insert tips into requested strict formats such as MCQ/PDF output. For photos, read only visible material and ask for clearer photos when uncertain. Never invent unreadable details. Answer in the student's language using readable plain text.`;
}
export function payload(input, model) {
  const turns = input.messages.map(m => ({ ...m }));
  if (input.photos.length) {
    turns.at(-1).content = [{ type: 'text', text: turns.at(-1).content }, ...input.photos.map(url => ({ type: 'image_url', image_url: { url } }))];
  }
  return {
    model, stream: false, max_tokens: model === KIMI ? 8192 : 4096,
    ...(model === KIMI ? { temperature: 1, reasoning_effort: 'low' } : model === NEMOTRON ? { reasoning: { enabled: false, exclude: true } } : model === GPT ? { reasoning_effort: 'low' } : {}),
    messages: [{ role: 'system', content: tutorPrompt(input) }, ...turns]
  };
}
async function query(input, model, key, fetcher) {
  let response;
  try {
    response = await fetcher(model !== NEMOTRON ? 'https://integrate.api.nvidia.com/v1/chat/completions' : 'https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(model === KIMI ? 180000 : 45000),
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}`, 'X-Title': 'Study Sprint' },
      body: JSON.stringify(payload(input, model))
    });
  } catch { throw new ChatError('unavailable', 'The AI service could not be reached. Please retry.', true); }
  if (!response.ok) {
    // Never pass the upstream response body, authorization header or key to clients/logs.
    await response.body?.cancel().catch(() => {});
    if ([401, 402, 403].includes(response.status)) throw new ChatError('failed-precondition', 'The shared chat service needs attention from the app owner. Please try later.');
    throw new ChatError('unavailable', 'The AI service is temporarily unavailable. Please retry.', [404, 408, 429].includes(response.status) || response.status >= 500);
  }
  const reader = response.body.getReader();
  let size = 0; const chunks = [];
  try {
    while (true) {
      const next = await reader.read(); if (next.done) break;
      size += next.value.byteLength;
      if (size > 1024 * 1024) { await reader.cancel(); throw new Error('large'); }
      chunks.push(Buffer.from(next.value));
    }
    const result = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    const choice = result.choices?.[0];
    const answer = choice?.message?.content;
    if (result.error || typeof answer !== 'string' || !answer.trim()) throw new Error('empty');
    return { answer: answer.trim().slice(0, 16000) + (choice.finish_reason === 'length' ? '\n\n[Response limit reached. Ask me to continue.]' : ''), model, fallback: model === KIMI };
  } catch { throw new ChatError('unavailable', 'No readable final answer was returned. Please retry.', true); }
  finally { reader.releaseLock(); }
}
export async function runChat(input, keys, reserveNvidia, fetcher = fetch) {
  const nvidia = /^nvapi-[A-Za-z0-9_-]{20,247}$/.test(keys?.nvidia ?? '');
  const router = /^sk-or-v1-[A-Za-z0-9_-]{20,247}$/.test(keys?.openrouter ?? '');
  // Photos always require a vision model. Text-only routes never receive images.
  const models = input.photos.length ? [KIMI] : input.model && input.model !== 'auto' ? [input.model] : [NEMOTRON, GPT, GLM, KIMI];
  let reserved = false; let last;
  for (const model of models) {
    if (model === NEMOTRON ? !router : !nvidia) {
      last = new ChatError('failed-precondition', 'This study model is not configured yet.');
      continue;
    }
    if (model !== NEMOTRON && !reserved) { await reserveNvidia(); reserved = true; }
    try { return await query(input, model, model === NEMOTRON ? keys.openrouter : keys.nvidia, fetcher); }
    catch (error) {
      if (!(error instanceof ChatError) || !error.retryable) throw error;
      last = error;
    }
  }
  throw last ?? new ChatError('unavailable', 'Study models are busy. Please retry.');
}
