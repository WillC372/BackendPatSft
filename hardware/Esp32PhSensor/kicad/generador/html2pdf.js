const { chromium } = require('playwright');
const [inp, out] = process.argv.slice(2);
(async () => { const b = await chromium.launch(); const p = await b.newPage();
  await p.goto('file://' + inp); await p.waitForTimeout(800);
  await p.pdf({ path: out, format: 'A4', landscape: true, printBackground: true, preferCSSPageSize: true });
  await b.close(); })();
