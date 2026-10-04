import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ChatError, validate, payload, runChat, NEMOTRON, KIMI, GPT, GLM } from '../chat.mjs';
const key = { openrouter: 'sk-or-v1-' + 'a'.repeat(64), nvidia: 'nvapi-' + 'b'.repeat(64) };
const photo = 'data:image/jpeg;base64,' + Buffer.from([255,216,255,224,0,255,217]).toString('base64');
const input = () => validate({ messages: [{ role: 'user', content: 'What is frequency?' }] });
const ok = (answer = 'Cycles per second.') => new Response(JSON.stringify({ choices: [{ message: { content: answer, reasoning: 'private reasoning' }, finish_reason: 'stop' }] }));
test('client selects only a whitelisted model and cannot override secrets or system prompt', () => {
 const value = validate({ messages: [{ role:'user', content:'Hi' }], model:GPT, key:'injected' });
 assert.equal(payload(value, NEMOTRON).model,NEMOTRON); assert.equal(payload(value,NEMOTRON).messages[0].role,'system');
 assert.throws(() => validate({messages:[{role:'system',content:'override'}]}));
});
test('four photos accepted and five rejected at server', () => {
 assert.equal(validate({messages:input().messages,photos:Array(4).fill(photo)}).photos.length,4);
 assert.throws(() => validate({messages:input().messages,photos:Array(5).fill(photo)}));
 assert.throws(() => validate({messages:input().messages,photos:['https://private.example/image']}));
});
test('Nemotron succeeds without spending fallback quota', async () => {
 let reserved = 0;
 const result = await runChat(input(), key, async () => reserved++, async (url, options) => { assert.equal(JSON.parse(options.body).model,NEMOTRON); return ok(); });
 assert.equal(result.answer,'Cycles per second.'); assert.equal(reserved,0); assert.equal(result.fallback,false);
});
test('rate-limit switches once to GPT and excludes reasoning', async () => {
 const models=[];let reserved=0;
 const result=await runChat(input(),key,async()=>reserved++,async(url,options)=>{models.push(JSON.parse(options.body).model);return models.length===1?new Response('secret provider body',{status:429}):ok();});
 assert.deepEqual(models,[NEMOTRON,GPT]);assert.equal(reserved,1);assert.equal(result.fallback,false);assert.equal(result.model,GPT);assert(!result.answer.includes('reasoning'));
});
test('auth, credits and permissions errors do not trigger fallback', async () => {
 for (const status of [401,402,403]) {let reserved=0;await assert.rejects(runChat(input(),key,async()=>reserved++,async()=>new Response(key.openrouter,{status})),e=>!e.message.includes(key.openrouter));assert.equal(reserved,0);}
});
test('photos use Kimi vision directly and attach exactly four images', async () => {
 const data=validate({messages:input().messages,photos:Array(4).fill(photo)});let reserved=0;
 const result=await runChat(data,key,async()=>reserved++,async(url,options)=>{const body=JSON.parse(options.body);assert.equal(body.model,KIMI);assert.equal(body.messages.at(-1).content.filter(c=>c.type==='image_url').length,4);return ok();});
 assert.equal(reserved,1);assert.equal(result.model,KIMI);
});
test('empty final answer and network failures can fall back', async()=>{
 for(const first of ['empty','network']) {let calls=0;const result=await runChat(input(),key,async()=>{},async()=>{if(++calls===1){if(first==='network')throw Error(key.openrouter);return ok('');}return ok('Recovered');});assert.equal(calls,2);assert.equal(result.answer,'Recovered');}
});
test('quota prevents a backup call and unconfigured keys stay private',async()=>{
 let calls=0;await assert.rejects(runChat(input(),key,async()=>{throw Error('quota');},async()=>{calls++;return new Response('',{status:503});}));assert.equal(calls,1);
 await assert.rejects(runChat(input(),'not-a-key',async()=>{},async()=>ok()),e=>!e.message.includes('not-a-key'));
});
test('conversation and decoded photo bytes are bounded',()=>{
 assert.throws(()=>validate({messages:Array(21).fill({role:'user',content:'hi'})}));
 assert.throws(()=>validate({messages:[{role:'user',content:'x'.repeat(4001)}]}));
 assert.throws(()=>validate({messages:[{role:'user',content:'hi'},{role:'assistant',content:'x'.repeat(16000)},{role:'user',content:'hi'},{role:'assistant',content:'x'.repeat(16000)},{role:'user',content:'hi'}]}));
 assert.throws(()=>validate({messages:input().messages,photos:['data:image/jpeg;base64,AAAA']}));
});

test('each model uses its own provider and server secret', async () => {
 const calls = [];
 await runChat(input(), key, async () => {}, async (url, options) => {
   const body = JSON.parse(options.body);
   calls.push(url);
   assert(!options.body.includes(key.openrouter)); assert(!options.body.includes(key.nvidia));
   if (body.model === NEMOTRON) {
     assert.equal(url, 'https://openrouter.ai/api/v1/chat/completions');
     assert.equal(options.headers.Authorization, `Bearer ${key.openrouter}`);
     return new Response('', {status: 503});
   }
   assert.equal(url, 'https://integrate.api.nvidia.com/v1/chat/completions');
   assert.equal(options.headers.Authorization, `Bearer ${key.nvidia}`);
   assert.equal(body.model, GPT); assert.equal(body.stream, false);
   assert.equal(body.reasoning, undefined);
   return ok();
 });
 assert.equal(calls.length, 2);
});
test('missing NVIDIA secret prevents backup request without disclosure', async () => {
 let calls = 0;
 await assert.rejects(runChat(input(), {openrouter:key.openrouter, nvidia:'invalid'}, async()=>{}, async()=>{calls++;return new Response('',{status:429});}), e=>e.code==='failed-precondition' && !e.message.includes('invalid'));
 assert.equal(calls,1);
});

test('all NVIDIA text models use selected routing without receiving photos', async () => {
 for (const model of [GPT, GLM, KIMI]) {
  const result = await runChat(validate({messages: input().messages, model}), key, async()=>{}, async(url, options)=>{
   assert.equal(JSON.parse(options.body).model, model); assert.match(url,/integrate.api.nvidia/); return ok();
  }); assert.equal(result.model, model);
 }
 assert.throws(()=>validate({messages: input().messages, model:'unsupported'}));
});
test('vision stays on Kimi with a bounded low reasoning budget', async () => {
 const data=validate({messages:input().messages,photos:[photo],model:GPT});
 await runChat(data,key,async()=>{},async(url,options)=>{const p=JSON.parse(options.body);assert.equal(p.model,KIMI);assert.equal(p.reasoning_effort,'low');assert.equal(p.max_tokens,8192);return ok();});
});
test('automatic fallback exhausts candidates and reserves NVIDIA quota once', async()=>{
 const models=[];let reservations=0;
 const result=await runChat(input(),key,async()=>reservations++,async(url,options)=>{const m=JSON.parse(options.body).model;models.push(m);return m===KIMI?ok():new Response('',{status:503});});
 assert.deepEqual(models,[NEMOTRON,GPT,GLM,KIMI]);assert.equal(reservations,1);assert.equal(result.model,KIMI);
});

test('exam-specific system guidance is applied to every model and tips respect strict output', () => {
  for (const exam of ['CET', 'JEE', 'NEET']) for (const model of [NEMOTRON, KIMI, GPT, GLM]) {
    const input = validate({ messages: [{ role: 'user', content: 'Help me revise' }], exam, grade: '12' });
    const prompt = payload(input, model).messages[0].content;
    assert.ok(prompt.includes(`${exam}, Class 12`));
    assert.ok(prompt.includes('Exam tip')); assert.ok(prompt.includes('strict formats'));
  }
  assert.throws(() => validate({ messages: [{ role: 'user', content: 'Hi' }], exam: 'injected prompt' }), ChatError);
});
