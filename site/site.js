// Tabs + the live demo. The demo imports the real morphex-ts engine built by
// morphex-ts/scripts/build-site.ts; this file contains no pluralization rules.
const SN = {
  "Maven": '<dependency>\n  <groupId>io.github.slayyyyer.morphex</groupId>\n  <artifactId>english-inflector-core</artifactId>\n  <version>1.3.0</version>\n</dependency>',
  "Gradle (Kotlin)": 'implementation("io.github.slayyyyer.morphex:english-inflector-core:1.3.0")',
  "Gradle (Groovy)": "implementation 'io.github.slayyyyer.morphex:english-inflector-core:1.3.0'",
  "npm (pending)": 'npm install morphex\n# not yet published — build from this repository instead:\ncd morphex-ts && npm install && npm run build',
  "From source": './mvnw install   # JDK 17+; wrapper pins Maven 3.9.9'
};
const tabs = document.getElementById('tabs');
const snip = document.getElementById('snip');
function pick(k) {
  if (!snip || !tabs) return;
  snip.textContent = SN[k];
  [...tabs.children].forEach(b => b.setAttribute('aria-selected', b.textContent === k));
}
if (tabs) {
  Object.keys(SN).forEach(k => {
    const b = document.createElement('button');
    b.className = 'tab';
    b.setAttribute('role', 'tab');
    b.textContent = k;
    b.onclick = () => pick(k);
    tabs.appendChild(b);
  });
  pick("Maven");
}

const demoInput = document.getElementById('w');
const out = document.getElementById('o');
const why = document.getElementById('y');
const detail = document.getElementById('detail');
const chips = document.getElementById('chips');
const note = document.getElementById('catalog-note');
const rulesToggle = document.getElementById('rules-only');
const FEATURED = ['mother-in-law', 'CHILD', 'UFO', 'die', 'blouse', 'scarp', 'German', 'cat'];

let enginePromise;
let timer;

function setNote(message) {
  if (note) note.textContent = message;
}

function loadEngine() {
  enginePromise ??= import('./demo/morphex-demo.js');
  return enginePromise;
}

function renderResult(result, mode) {
  if (out) out.textContent = result.plural;
  if (why) {
    why.textContent = `${result.input} → ${result.plural} · ${result.stage} (${result.source}) · ${result.countability}`;
  }
  if (detail) {
    const parts = [];
    if (result.ambiguous) parts.push('ambiguous');
    if (result.invariant) parts.push('invariant');
    if (result.alternatives.length) parts.push('alternatives: ' + result.alternatives.join(', '));
    if (result.note) parts.push(result.note);
    if (!result.diagnostics) parts.push('partial diagnostics');
    detail.textContent = parts.join(' · ');
  }
  setNote(
    mode === 'rules'
      ? 'Rules-only build: no lexicon data loaded. Irregular forms are lost by design.'
      : 'Full 147,338-entry lexicon loaded on demand (0.79 MB gzip). The membership sidecar is not loaded in the browser, so unknown uppercase initialism inference is disabled here.'
  );
}

async function runDemo() {
  if (!demoInput) return;
  const word = demoInput.value;
  if (!word.trim()) {
    if (out) out.textContent = '—';
    if (why) why.textContent = '';
    if (detail) detail.textContent = '';
    return;
  }
  const mode = rulesToggle && rulesToggle.checked ? 'rules' : 'bundled';
  try {
    const engine = await loadEngine();
    const result = engine.analyzeWord(word, mode);
    if (result) renderResult(result, mode);
  } catch (error) {
    setNote(`Engine unavailable (${error.message}). Rebuild with: cd morphex-ts && npm run build:site`);
  }
}

function scheduleDemo() {
  clearTimeout(timer);
  timer = setTimeout(runDemo, 120);
}

if (demoInput) {
  if (chips) {
    FEATURED.forEach(word => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'chip';
      button.textContent = word;
      button.onclick = () => {
        demoInput.value = word;
        runDemo();
      };
      chips.appendChild(button);
    });
  }
  demoInput.addEventListener('input', scheduleDemo);
  if (rulesToggle) rulesToggle.addEventListener('change', runDemo);
  runDemo();
}
