import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const root = new URL('../../', import.meta.url);
export async function androidData() {
  const gradle = await readFile(new URL('app/build.gradle.kts', root), 'utf8');
  const version = /versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"/.exec(gradle)?.[1];
  if (!version) throw Error('Could not read Android versionName');
  const source = await readFile(new URL('app/src/main/kotlin/com/pranav/study/cet_study_sprint/SyllabusData.kt', root), 'utf8');
  const syllabus = {};
  for (const name of ['cetEleven', 'cetTwelve', 'eleven', 'twelve']) {
    const block = new RegExp(`private val ${name} = mapOf\\(([\\s\\S]*?)\\n    \\)`).exec(source)?.[1];
    if (!block) throw Error(`Syllabus source changed: cannot parse ${name}; update pc/scripts/sync.mjs.`);
    const subjects = {};
    for (const match of block.matchAll(/"([^"]+)" to listOf\(([^\n]*)\)/g)) {
      const names = [...match[2].matchAll(/"(?:[^"\\]|\\.)*"/g)].map(item => JSON.parse(item[0]));
      if (!names.length) throw Error(`Empty chapter list: ${name} ${match[1]}`);
      subjects[match[1]] = names;
    }
    if (Object.keys(subjects).length < 3) throw Error(`Syllabus source changed: incomplete ${name}`);
    syllabus[name] = subjects;
  }
  const scoring = await readFile(new URL('app/src/main/kotlin/com/pranav/study/cet_study_sprint/QuizScoring.kt', root), 'utf8');
  const schemes = [...scoring.matchAll(/MarkingScheme\(correct = (-?\d+), incorrect = (-?\d+)\)/g)]
    .map(match => ({ correct: Number(match[1]), incorrect: Number(match[2]) }));
  if (schemes.length !== 2) throw Error('Scoring source changed: update PC sync script.');
  return { version, syllabus, marking: { CET: schemes[0], JEE: schemes[1], NEET: schemes[1] } };
}

export async function sync({ check = false } = {}) {
  const { version, syllabus, marking } = await androidData();
  const content = `// Generated from Android version, SyllabusData.kt and QuizScoring.kt. Run npm run sync.\nexport const PC_VERSION = ${JSON.stringify(version)};\nexport const SYLLABUS = ${JSON.stringify(syllabus, null, 2)};\nexport const MARKING = ${JSON.stringify(marking)};\n`;
  const target = new URL('../data.mjs', import.meta.url);
  if (check) {
    if (await readFile(target, 'utf8') !== content) throw Error('PC data is out of sync. Run npm run sync --prefix pc.');
  } else await writeFile(target, content);
  return version;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  console.log(`Study Sprint PC ${await sync({ check: process.argv.includes('--check') })}`);
}
