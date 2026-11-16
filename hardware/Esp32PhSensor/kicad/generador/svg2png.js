// node svg2png.js in.svg out.png width height [scale]
const { chromium } = require('playwright'); const fs = require('fs');
const [inp, out, w, h, scale = '1'] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: +scale });
  const svg = fs.readFileSync(inp, 'utf8').replace(/<svg([^>]*?)width="[^"]*"([^>]*?)height="[^"]*"/, '<svg$1$2').replace('<svg', `<svg style="width:${w}px;height:${h}px;display:block"`);
  await p.setContent(`<html><body style="margin:0;background:#fff">${svg}</body></html>`);
  await p.waitForTimeout(300);
  await p.screenshot({ path: out });
  await b.close();
})();
