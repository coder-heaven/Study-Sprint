// Reject malformed MCQs rather than silently dropping a fifth option or moving answers.
export function parseMcqs(raw, count = null) {
  const text = raw.replace(/```(?:text)?/g, '').replace(/\*\*/g, '').trim();
  const blocks = text.split(/(?=^\s*\d{1,2}[.)]\s+)/m).filter(s => s.trim());
  if (!blocks.length || blocks.length > 10 || (count !== null && blocks.length !== count)) throw Error('Invalid question count');
  return blocks.map((block, i) => {
    const lines = block.trim().split('\n').map(s => s.trim()).filter(Boolean);
    const heading = /^(\d+)[.)]\s+(.+)$/.exec(lines.shift() ?? '');
    if (!heading || Number(heading[1]) !== i + 1) throw Error('Invalid numbering');
    const options = []; let answer; let question = heading[2];
    for (const line of lines) {
      const option = /^\(?([A-Z])[.)]\s+(.+)$/.exec(line);
      const key = /^Answer:\s*([A-D])\s*$/i.exec(line);
      if (option) { if (option[1] !== String.fromCharCode(65 + options.length)) throw Error('Invalid options'); options.push(option[2]); }
      else if (key && answer === undefined) answer = key[1].toUpperCase();
      else if (!options.length && answer === undefined) question += ' ' + line;
      else throw Error('Unexpected MCQ text');
    }
    if (options.length !== 4 || new Set(options.map(s => s.toLowerCase())).size !== 4 || answer === undefined) throw Error('Exactly four distinct options required');
    return { question, options, answer };
  });
}
export function canonicalMcqs(items) {
  return items.map((q, i) => `${i + 1}. ${q.question}\n${q.options.map((o,n) => `${String.fromCharCode(65+n)}. ${o}`).join('\n')}\nAnswer: ${q.answer}`).join('\n\n');
}
export function hasOptions(text) { return /^\s*\(?A[.)]\s+/m.test(text) && /^\s*\(?B[.)]\s+/m.test(text); }
