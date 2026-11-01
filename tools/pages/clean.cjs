#!/usr/bin/env node
/*
 * Re-cleans saved pages with the current HyperExtract.snapshot() (extract.js),
 * in place. Use it on a page captured by an older Debug build, or after making
 * snapshot() stricter:
 *
 *   node tools/pages/clean.cjs tools/pages/saved/some-page.html [more.html …]
 *
 * Offline like check.cjs: the page is served from the file, everything else is blocked.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const EXTRACT = path.resolve(__dirname, '../../app/src/main/assets/extract.js');

(async () => {
  const files = process.argv.slice(2);
  if (!files.length) {
    console.log('Usage: node tools/pages/clean.cjs <saved page.html> [...]');
    process.exitCode = 1;
    return;
  }
  const browser = await chromium.launch();
  for (const file of files) {
    const html = fs.readFileSync(file, 'utf8');
    const m = html.match(/^\s*<!--\s*hf-url:\s*(\S+)\s*-->/);
    if (!m) { console.log(`skip ${file}: no <!-- hf-url: … --> first line`); continue; }
    const url = m[1];
    const context = await browser.newContext({ javaScriptEnabled: true });
    await context.route('**/*', (r) => (r.request().url() === url ? r.fulfill({ body: html, contentType: 'text/html; charset=utf-8' }) : r.abort()));
    const page = await context.newPage();
    await page.goto(url, { waitUntil: 'domcontentloaded' });
    await page.addScriptTag({ content: fs.readFileSync(EXTRACT, 'utf8') });
    const out = await page.evaluate(() => window.HyperExtract.snapshot());
    await context.close();
    fs.writeFileSync(file, out);
    console.log(`cleaned ${file}: ${(html.length / 1e6).toFixed(2)} MB → ${(out.length / 1e6).toFixed(2)} MB`);
  }
  await browser.close();
})();
