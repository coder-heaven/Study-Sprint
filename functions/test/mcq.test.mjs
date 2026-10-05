import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validate,runChat,GPT,NEMOTRON,KIMI,GLM,MUSE,MISTRAL} from '../chat.mjs';
import {parseMcqs} from '../mcq.mjs';
const quiz='1. Photon energy is?\nA. h*f\nB. h/f\nC. f/h\nD. h+f\nAnswer: A';
const keys={gemini:'AQ.'+'c'.repeat(36),openrouter:'sk-or-v1-'+'a'.repeat(64),nvidia:'nvapi-'+'b'.repeat(64),mistral:'m'.repeat(32)};
const provider=text=>new Response(JSON.stringify({choices:[{message:{content:text},finish_reason:'stop'}]}));
const grounded=(title='MHT-CET Physics questions',text=quiz)=>new Response(JSON.stringify({candidates:[{finishReason:'STOP',content:{parts:[{text}]},groundingMetadata:{webSearchQueries:['MHT-CET photon energy MCQ'],groundingChunks:[{web:{uri:'https://example.com/cet',title}}],groundingSupports:[{segment:{text},groundingChunkIndices:[0]}],searchEntryPoint:{renderedContent:'<div>Search suggestions</div>'}}}]}));
test('rejects fifth option without silently deleting or remapping it',()=>{
 assert.throws(()=>parseMcqs(quiz.replace('Answer:', 'E. other\nAnswer:')));
 assert.throws(()=>parseMcqs(quiz.replace('Answer: A','Answer: E')));
 assert.equal(parseMcqs(quiz)[0].options.length,4);
 assert.throws(()=>parseMcqs(quiz.replace('Answer:', 'E. extra\nAnswer:').replace(/^([A-E])\./gm,'- **$1.**')));
 assert.equal(parseMcqs(quiz.replace(/^([A-D])\./gm,'- **$1:**'))[0].options.length,4);
});
test('five-option output is not returned by any selected study model',async()=>{
 for(const model of [GPT,NEMOTRON,KIMI,GLM,MUSE,MISTRAL]) await assert.rejects(runChat(validate({messages:[{role:'user',content:'Explain energy'}],model}),keys,async()=>{},async()=>provider(quiz.replace('Answer:', 'E. extra\nAnswer:'))));
});
test('every selected model returns verified web MCQs directly without a tutor call',async()=>{
 for(const model of [GPT,NEMOTRON,KIMI,GLM,MUSE,MISTRAL]) {
  const calls=[];const result=await runChat(validate({messages:[{role:'user',content:'Photon energy'}],model,mode:'web_mcq'}),keys,async()=>{},async(url,options)=>{
   calls.push(url);const body=JSON.parse(options.body);
   if(calls.length===1){assert.ok(body.tools[0].google_search);assert.equal(options.headers['x-goog-api-key'],keys.gemini);assert.ok(!options.body.includes(keys.gemini));return grounded();}
   assert.equal(body.model,model);assert.ok(body.messages.at(-1).content.includes(quiz));return provider(quiz);
  }); assert.equal(calls.length,1);assert.equal(result.model,'gemini-2.5-flash');assert.equal(result.answer,quiz);assert.equal(result.quiz,true);assert.equal(result.sources.length,1);
 }
});
test('NEET profile uses NEET search and accepts only NEET source titles',async()=>{
 const input=validate({messages:[{role:'user',content:'Photon energy'}],mode:'web_mcq',exam:'NEET'});
 let count=0;const result=await runChat(input,keys,async()=>{},async(url,o)=>{if(++count===1){assert.match(o.body,/NEET UG/);return grounded('NEET UG physics questions');}return provider(quiz);});assert.equal(result.quiz,true);assert.equal(count,1);
});
test('no source, wrong exam and malformed MCQs fail closed',async()=>{
 for(const first of [grounded('NEET Physics questions'),grounded('generic physics'),grounded('MHT-CET Physics',quiz.replace('Answer:', 'E. extra\nAnswer:'))]) {
  let calls=0;
  await assert.rejects(runChat(validate({messages:[{role:'user',content:'energy'}],mode:'web_mcq',model:GPT}),keys,async()=>{},async()=>{calls++;return first;}));
  assert.equal(calls,1);
 }
});
test('missing Google configuration never fabricates searched questions',async()=>{
 let calls=0;await assert.rejects(runChat(validate({messages:[{role:'user',content:'energy'}],mode:'web_mcq'}),{},async()=>{},async()=>{calls++;return provider(quiz);}),e=>e.code==='failed-precondition');assert.equal(calls,0);
});
test('photo PDF prefers Gemini with four-option schema and returns canonical ten questions',async()=>{
 const photo='data:image/jpeg;base64,'+Buffer.from([255,216,255,224,0,255,217]).toString('base64');
 let reservations=0;
 const result=await runChat(validate({messages:[{role:'user',content:'Create photo questions'}],photos:[photo],mode:'photo_pdf'}),keys,async()=>reservations++,async(url,o)=>{
  assert.match(url,/generativelanguage/);const b=JSON.parse(o.body);assert.equal(b.generationConfig.responseSchema.items.properties.options.maxItems,4);assert.ok(b.contents[0].parts[1].inlineData);
  return new Response(JSON.stringify({candidates:[{finishReason:'STOP',content:{parts:[{text:JSON.stringify(Array(10).fill({question:'Photon energy is?',options:['h*f','h/f','f/h','h+f'],answer:'A'}))}]}}]}));
 });assert.equal(result.model,'gemini-2.5-flash');assert.equal(parseMcqs(result.answer,10).length,10);assert.equal(reservations,0);
});


test('photo PDF also retries the second Google key without exposing either key', async () => {
 const second='AQ.'+'d'.repeat(36);
 const photo='data:image/jpeg;base64,'+Buffer.from([255,216,255,224,0,255,217]).toString('base64');
 const used=[];
 const result=await runChat(validate({messages:[{role:'user',content:'Create photo questions'}],photos:[photo],mode:'photo_pdf'}),
  {...keys,geminiBackup:second},async()=>{},async(_url,o)=>{
   used.push(o.headers['x-goog-api-key']);
   return used.length===1 ? new Response('private provider response',{status:429}) :
    new Response(JSON.stringify({candidates:[{finishReason:'STOP',content:{parts:[{text:JSON.stringify(Array(10).fill({question:'Photon energy is?',options:['h*f','h/f','f/h','h+f'],answer:'A'}))}]}}]}));
  });
 assert.deepEqual(used,[keys.gemini,second]);assert.equal(parseMcqs(result.answer,10).length,10);
});
test('online MCQs need only Google and do not spend NVIDIA fallback quota', async () => {
 let calls=0;let reserves=0;
 const result=await runChat(validate({messages:[{role:'user',content:'Mole concept'}],mode:'web_mcq'}),{gemini:keys.gemini},async()=>reserves++,async(url)=>{
  assert.match(url,/generativelanguage/);assert.equal(++calls,1);return grounded();
 });
 assert.equal(result.answer,quiz);assert.equal(result.quiz,true);assert.equal(reserves,0);
});
test('online MCQs switch to a second Google key after quota or key permission errors', async () => {
 const second='AQ.'+'d'.repeat(36);
 for (const status of [429,401,403]) {
  const used=[];
  const result=await runChat(validate({messages:[{role:'user',content:'Photon energy'}],mode:'web_mcq'}),
   {...keys,geminiBackup:second},async()=>{},async(_url,options)=>{
    used.push(options.headers['x-goog-api-key']);
    return used.length===1 ? new Response('private provider response',{status}) : grounded();
   });
  assert.deepEqual(used,[keys.gemini,second]);assert.equal(result.answer,quiz);
 }
});
test('Google backup handles absent primary and never retries bad requests or duplicate keys', async () => {
 const input=validate({messages:[{role:'user',content:'Photon energy'}],mode:'web_mcq'});
 const second='AQ.'+'d'.repeat(36);
 let used=[];
 assert.equal((await runChat(input,{geminiBackup:second},async()=>{},async(_url,o)=>{
  used.push(o.headers['x-goog-api-key']);return grounded();
 })).answer,quiz);
 assert.deepEqual(used,[second]);
 for (const [status,backup] of [[400,second],[429,keys.gemini],[503,second]]) {
  used=[];
  await assert.rejects(runChat(input,{gemini:keys.gemini,geminiBackup:backup},async()=>{},async(_url,o)=>{
   used.push(o.headers['x-goog-api-key']);return new Response('private provider response',{status});
  }),error=>!error.message.includes(keys.gemini));
  assert.deepEqual(used,[keys.gemini]);
 }
});
test('Google quota and setup failures are distinct and never expose provider text', async () => {
 for(const [status,code] of [[429,'resource-exhausted'],[400,'failed-precondition'],[403,'failed-precondition'],[503,'unavailable']]) {
  await assert.rejects(runChat(validate({messages:[{role:'user',content:'Mole concept'}],mode:'web_mcq'}),keys,async()=>{},async()=>new Response(keys.gemini,{status})),e=>e.code===code&&!e.message.includes(keys.gemini));
 }
});
