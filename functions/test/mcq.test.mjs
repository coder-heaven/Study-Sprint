import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validate, runChat, GPT, NEMOTRON, KIMI, GLM, MUSE, MISTRAL } from '../chat.mjs';
import { parseMcqs } from '../mcq.mjs';
import { studyTopic } from '../tavily.mjs';
const quiz = '1. Photon energy is?\nA. h*f\nB. h/f\nC. f/h\nD. h+f\nAnswer: A';
const five = Array.from({length:5},(_,i)=>quiz.replace('1. Photon', `${i+1}. Photon ${i+1}`)).join('\n\n');
const keys = { tavily: 'tvly-' + 't'.repeat(32), openrouter: 'sk-or-v1-' + 'a'.repeat(64), nvidia: 'nvapi-' + 'b'.repeat(64), mistral: 'm'.repeat(32) };
const provider = text => new Response(JSON.stringify({ choices: [{ message: { content: text }, finish_reason: 'stop' }] }));
const search = (title = 'MHT-CET Physics questions', raw = quiz, url = 'https://questions.examside.com/past-years/jee/mht-cet') =>
  new Response(JSON.stringify({ results: [{ title, url, raw_content: raw, content: raw }] }));
const photo = 'data:image/jpeg;base64,' + Buffer.from([255,216,255,224,0,255,217]).toString('base64');
const ten = Array.from({ length: 10 }, (_, i) => quiz.replace('1.', `${i + 1}.`)).join('\n\n');

test('rejects fifth option without silently deleting or remapping it', () => {
  assert.throws(() => parseMcqs(quiz.replace('Answer:', 'E. other\nAnswer:')));
  assert.throws(() => parseMcqs(quiz.replace('Answer: A', 'Answer: E')));
  assert.equal(parseMcqs(quiz)[0].options.length, 4);
});
test('five-option output is not returned by any selected study model', async () => {
  for (const model of [GPT, NEMOTRON, KIMI, GLM, MUSE, MISTRAL])
    await assert.rejects(runChat(validate({ messages: [{ role: 'user', content: 'Explain energy' }], model }), keys, async () => {}, async () => provider(quiz.replace('Answer:', 'E. extra\nAnswer:'))));
});
test('online MCQ uses Tavily only, with bounded search and no secret in body', async () => {
  for (const model of [GPT, NEMOTRON, KIMI, GLM, MUSE, MISTRAL]) {
    let calls = 0; let reserves = 0;
    const result = await runChat(validate({ messages: [{ role: 'user', content: 'Photon energy' }], model, mode: 'web_mcq' }), keys, async () => reserves++, async (url, options) => {
      calls++; assert.equal(url, 'https://api.tavily.com/search');
      assert.equal(options.headers.Authorization, `Bearer ${keys.tavily}`);
      const body = JSON.parse(options.body);
      assert.equal(body.search_depth, 'basic'); assert.equal(body.include_answer, false);
      assert.equal(body.include_domains_mode, 'restrict'); assert.deepEqual(body.include_domains, ['examside.com','selfstudys.com','prepizo.com']);
      assert.ok(body.max_results <= 5); assert.ok(!options.body.includes(keys.tavily));
      return search();
    });
    assert.equal(calls, 1); assert.equal(reserves, 0); assert.equal(result.model, 'tavily/search');
    assert.equal(result.answer, quiz); assert.equal(result.sources.length, 1); assert.equal(result.quiz, true);
  }
});
test('NEET and JEE source titles must match the selected exam', async () => {
  for (const [exam, title] of [['NEET', 'NEET UG physics questions'], ['JEE', 'JEE Main physics questions']]) {
    const result = await runChat(validate({ messages: [{ role: 'user', content: 'Photon energy' }], exam, mode: 'web_mcq' }), keys, async () => {}, async (_url, options) => {
      const body=JSON.parse(options.body);
      assert.ok(body.query.includes(exam === 'NEET' ? 'NEET UG' : 'JEE Main'));
      assert.ok(body.include_domains.includes('examgoal.com'));
      return search(title, quiz, 'https://www.examgoal.com/questions/physics');
    });
    assert.equal(result.answer, quiz);
  }
});
test('approved SelfStudys and Prepizo subdomains can supply complete CET questions', async () => {
  for (const url of ['https://www.selfstudys.com/books/mhtcet-previous-year-paper', 'https://practice.prepizo.com/mht-cet/mcq']) {
    const result=await runChat(validate({messages:[{role:'user',content:'Photon energy'}],mode:'web_mcq'}),keys,async()=>{},async()=>search('MHT-CET Physics questions',quiz,url));
    assert.equal(result.answer,quiz); assert.equal(result.sources[0].url,url);
  }
});
test('wrong-exam and insecure sources never enter tutor context or cited questions', async () => {
  const bad = [search('NEET Physics questions'), search('generic physics'), search('MHT-CET Physics', quiz, 'http://example.com/cet'),
    search('MHT-CET Physics', quiz, 'https://user:pass@questions.examside.com/cet'),
    search('MHT-CET Physics', quiz, 'https://examside.com.evil.example/cet'),
    search('MHT-CET Physics', quiz, 'https://unlisted.example/cet'),
    search('MHT-CET Physics', quiz, 'https://www.examgoal.com/cet'),
    search('MHT-CET Physics', quiz, 'https://questions.examside.com/past-years/jee/jee-main/physics'),
    search('MHT-CET Physics', quiz, 'https://questions.examside.com/past-years/jee/mht-cet-biology/physics')];
  for (const response of bad) {
    let calls=0;
    const result=await runChat(validate({ messages: [{ role: 'user', content: 'energy' }], mode: 'web_mcq' }), keys, async()=>{}, async (_url, options) => {
      if (++calls===1) return response;
      const body=JSON.parse(options.body);
      assert.ok(!body.messages.at(-1).content.includes('Photon energy is?'));
      assert.ok(body.messages.at(-1).content.includes('MHT-CET'));
      return provider(five);
    });
    assert.equal(calls,2);assert.deepEqual(result.sources,[]);assert.equal(result.answer,five);
  }
});
test('exam-specific chapter snippets become original practice, never fake past-paper citations', async () => {
  for (const [exam,title,url] of [
    ['CET','MHT CET chapter questions','https://questions.examside.com/past-years/jee/mht-cet/physics/motion'],
    ['JEE','JEE Main chapter questions','https://questions.examside.com/past-years/jee/jee-main/physics/motion'],
    ['NEET','NEET UG chapter questions','https://questions.examside.com/past-years/medical/neet/physics/motion']
  ]) {
    const calls=[];let reserves=0;
    const result=await runChat(validate({messages:[{role:'user',content:'Motion'}],exam,mode:'web_mcq'}),keys,async()=>reserves++,async(endpoint,options)=>{
      calls.push(endpoint);
      if (calls.length===1) return search(title,'Physics / Motion / 2026 12 questions 1.5% weightage',url);
      const body=JSON.parse(options.body);
      assert.equal(body.model,GPT);assert.match(body.messages.at(-1).content, /ORIGINAL/);
      assert.ok(body.messages.at(-1).content.includes({CET:'MHT-CET',JEE:'JEE Main',NEET:'NEET UG'}[exam]));
      return provider(five);
    });
    assert.equal(calls.length,2);assert.equal(reserves,1);assert.equal(result.quiz,true);
    assert.equal(result.answer,five);assert.equal(result.model,GPT);
    assert.ok(result.sources[0].title.startsWith('Study context:'));
  }
});
test('exact five-MCQ chemistry practice skips Tavily and confines original questions to chosen exam and chapter', async () => {
  const prompt='Give me 5 mcq based on some basic concepts of chemistry for practice\nDifficulty: Medium. Choose standard exam-level questions with a moderate number of steps.';
  assert.equal(studyTopic(prompt),'some basic concepts of chemistry');
  for (const exam of ['CET','JEE','NEET']) {
    const calls=[];
    const result=await runChat(validate({messages:[{role:'user',content:prompt}],exam,mode:'web_mcq'}),keys,async()=>{},async(url,options)=>{
      calls.push({url,body:JSON.parse(options.body)});
      return provider(five);
    });
    assert.equal(calls.length,1); assert.ok(!calls[0].url.includes('tavily'));
    const tutor=calls[0].body.messages.at(-1).content;
    assert.match(tutor,/exactly 5 ORIGINAL/); assert.match(tutor,/Difficulty: Medium/);
    assert.match(tutor,/ONLY about this topic: some basic concepts of chemistry/);
    assert.match(tutor,/LaTeX equations/);
    assert.ok(tutor.includes({CET:'MHT-CET',JEE:'JEE Main',NEET:'NEET UG'}[exam]));
    assert.equal(result.answer,five); assert.deepEqual(result.sources,[]);
  }
});
test('wrong chapter search hits never appear as copied MCQs or study context', async () => {
  const calls=[];
  const input=validate({messages:[{role:'user',content:'Give me 5 MCQs on some basic concepts of chemistry'}],mode:'web_mcq'});
  const result=await runChat(input,keys,async()=>{},async(url,options)=>{
    calls.push({url,body:JSON.parse(options.body)});
    return calls.length===1 ? search('MHT-CET Redox Reactions',five.replaceAll('Photon','Redox'), 'https://questions.examside.com/past-years/jee/mht-cet/chemistry/redox-reactions') : provider(five);
  });
  assert.equal(calls.length,2);assert.match(calls[0].body.query,/some basic concepts of chemistry/);
  assert.ok(!calls[0].body.query.includes('Difficulty:'));
  assert.ok(!calls[1].body.messages.at(-1).content.includes('Redox Reactions'));
  assert.deepEqual(result.sources,[]);assert.equal(result.answer,five);
});
test('LaTeX equation and chemistry notation survive original practice validation', async () => {
  const prompt='Give me 1 MCQ on some basic concepts of chemistry for practice';
  const math='1. Calculate $n = \\frac{m}{M}$ for 18 g of water.\nA. $0.5\\,\\mathrm{mol}$\nB. $1\\,\\mathrm{mol}$\nC. $2\\,\\mathrm{mol}$\nD. $18\\,\\mathrm{mol}$\nAnswer: B';
  const result=await runChat(validate({messages:[{role:'user',content:prompt}],mode:'web_mcq'}),keys,async()=>{},async()=>provider(math));
  assert.equal(result.answer,math);assert.equal(result.quiz,true);
});
test('redox or MBA tutor output is rejected for basic-chemistry practice', async () => {
  const input=validate({messages:[{role:'user',content:'Give me 5 MCQs on some basic concepts of chemistry for practice'}],mode:'web_mcq'});
  const responses=[five.replaceAll('Photon','Redox'),five.replaceAll('Photon','MBA'),five];
  let calls=0;
  const result=await runChat(input,keys,async()=>{},async()=>provider(responses[calls++]));
  assert.equal(calls,3);assert.equal(result.answer,five);
});
test('partial, fifth-option and missing-answer sources are not copied, generated practice must be complete', async () => {
  for (const raw of [quiz.replace('Answer: A',''), quiz.replace('Answer:', 'E. extra\nAnswer:'), 'Physics Motion 2026 12 questions']) {
    let calls=0;
    await assert.rejects(runChat(validate({messages:[{role:'user',content:'Motion'}],mode:'web_mcq'}),keys,async()=>{},async(_url)=>{
      calls++;return calls===1 ? search('MHT-CET Motion',raw) : provider('1. Incomplete question');
    }),e=>e.code==='unavailable');
    assert.equal(calls,4); // one Tavily search and three bounded tutor attempts
  }
});
test('missing Tavily key does not call search or fabricate questions', async () => {
  let calls = 0;
  await assert.rejects(runChat(validate({ messages: [{ role: 'user', content: 'energy' }], mode: 'web_mcq' }), {}, async () => {}, async () => { calls++; return search(); }),
    e => e.code === 'failed-precondition' && e.message.includes('TAVILY_API_KEY'));
  assert.equal(calls, 0);
});
test('second Tavily key is used only for quota or auth errors, with no key in errors', async () => {
  const second='tvly-'+'s'.repeat(32);
  const input=validate({messages:[{role:'user',content:'Photon energy'}],mode:'web_mcq'});
  for (const status of [429,432,433,401,403]) {
    const used=[];
    const result=await runChat(input,{...keys,tavilyBackup:second},async()=>{},async(_url,options)=>{
      used.push(options.headers.Authorization);
      return used.length===1 ? new Response('private provider response',{status}) : search();
    });
    assert.equal(result.answer,quiz); assert.deepEqual(used,[`Bearer ${keys.tavily}`,`Bearer ${second}`]);
  }
  let used=[];
  assert.equal((await runChat(input,{tavilyBackup:second},async()=>{},async(_url,options)=>{
    used.push(options.headers.Authorization);return search();
  })).answer,quiz);
  assert.deepEqual(used,[`Bearer ${second}`]);
  for (const [status,backup] of [[503,second],[429,keys.tavily]]) {
    used=[];
    await assert.rejects(runChat(input,{...keys,tavilyBackup:backup},async()=>{},async(_url,options)=>{
      used.push(options.headers.Authorization);return new Response(keys.tavily,{status});
    }),e=>!e.message.includes(keys.tavily));
    assert.deepEqual(used,[`Bearer ${keys.tavily}`]);
  }
  used=[];
  const original=await runChat(input,{...keys,tavilyBackup:second},async()=>{},async(_url,options)=>{
    used.push(options.headers.Authorization);
    return used.length===1 ? search('NEET questions') : provider(five);
  });
  assert.equal(original.answer,five);
  assert.deepEqual(original.sources,[]);
  assert.deepEqual(used,[`Bearer ${keys.tavily}`,`Bearer ${keys.nvidia}`]);
});
test('photo PDF uses NVIDIA vision and rejects an incomplete ten-question reply', async () => {
  let calls = 0; let reserves = 0;
  const input = validate({ messages: [{ role: 'user', content: 'Create photo questions' }], photos: [photo], mode: 'photo_pdf' });
  const result = await runChat(input, keys, async () => reserves++, async (url, options) => {
    calls++; assert.match(url, /nvidia/); assert.equal(options.headers.Authorization, `Bearer ${keys.nvidia}`);
    const body = JSON.parse(options.body); assert.match(body.messages.at(-1).content[0].text, /exactly 10/);
    assert.equal(body.messages.at(-1).content[1].image_url.url, photo);
    return provider(ten);
  });
  assert.equal(result.model, KIMI); assert.equal(parseMcqs(result.answer, 10).length, 10);
  assert.equal(calls, 1); assert.equal(reserves, 1);
  await assert.rejects(runChat(input, keys, async () => {}, async () => provider(quiz)), e => e.code === 'unavailable');
});
test('search provider quota and auth errors are safe and distinct', async () => {
  for (const [status, code] of [[429, 'resource-exhausted'], [432, 'resource-exhausted'], [401, 'failed-precondition'], [503, 'unavailable']]) {
    await assert.rejects(runChat(validate({ messages: [{ role: 'user', content: 'Mole concept' }], mode: 'web_mcq' }), keys, async () => {}, async () => new Response(keys.tavily, { status })),
      e => e.code === code && !e.message.includes(keys.tavily));
  }
});
