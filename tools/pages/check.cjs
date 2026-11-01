#!/usr/bin/env node
/*
 * Runs the app's page scripts (assets/extract.js, assets/bridge.js) against saved
 * Fragrantica pages in headless Chromium, fully offline, and compares what they
 * report with the expected JSON next to each page.
 *
 *   node tools/pages/check.cjs            check every page in tools/pages/saved/
 *   node tools/pages/check.cjs --update   accept the current output as expected
 *   node tools/pages/check.cjs black-opium   only pages whose name contains this
 *
 * Saved pages come from the Debug app: FRAGRANTICA (or ⋮ on a perfume) →
 * "Share page for testing". Each file starts with <!-- hf-url: … --> so the page
 * is served at its real address (the scripts check location.pathname).
 * No request leaves the machine: everything but the saved page is blocked.
 *
 * Exit code 1 when a page reports nothing or its output differs from the expected
 * JSON. ✗ marks sections that came out empty: on a real perfume page that usually
 * means a selector in extract.js's X object no longer matches.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '../..');
const ASSETS = path.join(ROOT, 'app/src/main/assets');
const SAVED = path.join(__dirname, 'saved');
const OUT = path.join(ROOT, 'build/pages');

const args = process.argv.slice(2);
const update = args.includes('--update');
const filter = args.find((a) => !a.startsWith('--'));

// What the WebView gets at document start (SiteController.documentStartScript), minus the CSS.
// Android runs document-start scripts once <html> exists; Chromium's init scripts run
// before that, so wait for it.
const scripts = [
  fs.readFileSync(path.join(ASSETS, 'extract.js'), 'utf8'),
  fs.readFileSync(path.join(ASSETS, 'bridge.js'), 'utf8'),
].join('\n');
const startScript = `
window.__hf = [];
window.VitrineNative = {
  onDetail: function (j) { window.__hf.push({ fn: 'onDetail', json: j }); },
  onState: function (j) { window.__hf.push({ fn: 'onState', json: j }); }
};
(function () {
  function run() { ${scripts}
  }
  if (document.documentElement) return run();
  new MutationObserver(function (_, mo) {
    if (document.documentElement) { mo.disconnect(); run(); }
  }).observe(document, { childList: true });
})();`;

// Sections the native perfume screen shows; one missing usually means a selector broke.
const DETAIL_SECTIONS = ['identity', 'rating', 'accords', 'notes', 'longevity', 'sillage', 'wear', 'prosCons', 'similar', 'alsoLike', 'reviews'];

function pageUrl(html, file) {
  const m = html.match(/^\s*<!--\s*hf-url:\s*(\S+)\s*-->/);
  if (!m) throw new Error(`${file}: first line must be <!-- hf-url: https://www.fragrantica.com/… -->`);
  return m[1];
}

async function run(browser, file) {
  const html = fs.readFileSync(path.join(SAVED, file), 'utf8');
  const url = pageUrl(html, file);
  const isPerfume = new URL(url).pathname.startsWith('/perfume/');
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, javaScriptEnabled: true });
  await context.route('**/*', (route) =>
    route.request().url() === url ? route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: html }) : route.abort());
  await context.addInitScript(startScript);
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e.stack || e).split('\n').slice(0, 3).join(' | ')));
  await page.goto(url, { waitUntil: 'domcontentloaded' });

  // Perfume pages: wait for the final "detail" report (loadAll may scroll for a few seconds).
  // Search pages: the bridge reports state shortly after load; take the last one.
  const deadline = Date.now() + 20000;
  let result = null;
  while (Date.now() < deadline) {
    const msgs = await page.evaluate(() => window.__hf.splice(0));
    for (const m of msgs) {
      const o = JSON.parse(m.json);
      if (isPerfume && m.fn === 'onDetail' && o.kind === 'detail') result = o.data;
      if (!isPerfume && m.fn === 'onState' && o.page === 'search') result = o;
    }
    if (result && isPerfume) break;
    await page.waitForTimeout(500);
    if (result && !isPerfume && Date.now() > deadline - 15000) break;
  }
  await context.close();
  return { url, isPerfume, result, errors };
}

function summary(r) {
  if (!r.result) return ['nothing reported'];
  if (r.isPerfume) {
    return DETAIL_SECTIONS.map((k) => {
      const v = r.result[k];
      const n = v == null ? 0 : Array.isArray(v) ? v.length : k === 'reviews' ? (v.items || []).length : k === 'prosCons' ? v.pros.length + v.cons.length : 1;
      return `${n ? '✓' : '✗'} ${k}${Array.isArray(v) || k === 'reviews' || k === 'prosCons' ? ` (${n})` : ''}`;
    });
  }
  const s = r.result;
  // The result count comes from the live InstantSearch instance or Algolia responses, never the HTML.
  return [`${s.hitList.length ? '✓' : '✗'} hits (${s.hitList.length})`, `– total (live page only)`,
    `${s.panels.length ? '✓' : '✗'} filter panels (${s.panels.length})`, `${s.sort && s.sort.options.length ? '✓' : '✗'} sort options`];
}

// Volatile bits that change between captures without meaning anything.
function stable(o) {
  return JSON.parse(JSON.stringify(o, (k, v) => (k === 'url' && typeof v === 'string' ? v.replace(/[?#].*$/, '') : v)));
}

// Saved pages are committed to a public repo: HyperExtract.snapshot() must strip
// members' names, review text, scripts, frames and ads. Checked on every run.
async function privacyCheck(browser) {
  const url = 'https://www.fragrantica.com/perfume/Privacy/Check-1.html';
  // One of each place member data has turned up on real pages.
  const html = `<html><head><script>var tracking = 1</script></head><body>
    <div class="tw-review-card" id="parent1"><meta itemprop="name" content="RealMemberName">
    <a href="https://www.fragrantica.com/@realhandle"><img src="https://fimgs.net/mdimg/avatari/s.1.jpg" alt="RealMemberName"></a>
    <a href="/member/12345">RealMemberName</a>
    <p>Posted by RealMemberName</p><div class="tw-review-prose"><p>Words from a private review.</p></div></div>
    <div class="tw-photo-card"><a data-caption="<a href='https://www.fragrantica.com/fragram/fp/1'>PhotoMember (Fragrantica.com)</a>">
      <img src="https://fimgs.net/photogram/p180/x.jpg" alt="Photo by PhotoMember"></a></div>
    <a href="/perfume/X/Y-2.html"><span class="group/tooltip cursor-help"> WidgetMember <div>Snippet of another review.</div></span>
      <img src="https://fimgs.net/himg/o.1.png" alt="WidgetMember"></a>
    <p>Thanks to PhotoMember and realhandle.</p>
    <iframe src="https://ads.example"></iframe><div id="div-gpt-ad-1">ad</div></body></html>`;
  const context = await browser.newContext();
  await context.route('**/*', (r) => (r.request().url() === url ? r.fulfill({ body: html, contentType: 'text/html' }) : r.abort()));
  const page = await context.newPage();
  await page.goto(url);
  await page.addScriptTag({ content: fs.readFileSync(path.join(ASSETS, 'extract.js'), 'utf8') });
  const out = await page.evaluate(() => window.HyperExtract.snapshot());
  await context.close();
  return ['RealMemberName', 'realhandle', 'PhotoMember', 'WidgetMember', 'private review', 'another review', 'avatari', 'fragram/fp',
    '<script', '<iframe', 'div-gpt', '/member/12345'].filter((s) => out.includes(s));
}

(async () => {
  {
    const browser = await chromium.launch();
    const leaks = await privacyCheck(browser);
    await browser.close();
    if (leaks.length) {
      console.log(`FAIL     snapshot privacy: saved pages would still contain ${leaks.join(', ')}`);
      process.exitCode = 1;
      return;
    }
    console.log('OK       snapshot privacy (review authors and text, photo credits, avatars, profile links, scripts and ads are removed)');
  }
  if (!fs.existsSync(SAVED)) fs.mkdirSync(SAVED, { recursive: true });
  const files = fs.readdirSync(SAVED).filter((f) => f.endsWith('.html') && (!filter || f.includes(filter))).sort();
  if (!files.length) {
    console.log(`No saved pages in ${path.relative(ROOT, SAVED)}/. Capture one with the Debug app: "Share page for testing".`);
    return;
  }
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  let failed = 0;
  for (const file of files) {
    const name = file.replace(/\.html$/, '');
    const r = await run(browser, file);
    const actual = r.result ? JSON.stringify(stable(r.result), null, 2) + '\n' : null;
    const expectedFile = path.join(SAVED, `${name}.expected.json`);
    const lines = summary(r);
    let status;
    if (!actual) status = 'FAIL';
    else if (update) { fs.writeFileSync(expectedFile, actual); status = 'UPDATED'; }
    else if (!fs.existsSync(expectedFile)) status = 'NEW (run with --update to accept)';
    else status = fs.readFileSync(expectedFile, 'utf8') === actual ? 'OK' : 'CHANGED';
    if (actual) fs.writeFileSync(path.join(OUT, `${name}.actual.json`), actual);
    if (status === 'FAIL' || status === 'CHANGED') failed++;
    console.log(`\n${status.padEnd(8)} ${file}\n  ${lines.join('\n  ')}`);
    if (status === 'CHANGED') console.log(`  diff: diff ${path.relative(ROOT, expectedFile)} ${path.relative(ROOT, path.join(OUT, name + '.actual.json'))}`);
    for (const e of r.errors) console.log(`  page error: ${e}`);
  }
  await browser.close();
  console.log(failed ? `\n${failed} of ${files.length} page(s) need attention.` : `\nAll ${files.length} page(s) OK.`);
  process.exitCode = failed ? 1 : 0;
})();
