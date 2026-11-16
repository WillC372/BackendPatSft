// node render-pcb.js <out.png> [view] [w] [h] [hideRegex] [glbOut]
const { chromium } = require('playwright');
const http = require('http'); const fs = require('fs'); const path = require('path');
const [out, view = 'iso', w = '1600', h = '1000', hide = '', glbOut = ''] = process.argv.slice(2);
const ROOT = __dirname; const ALLOWED = ['/usr/share/kicad/3dmodels/', '/home/user/backendpatsft/hardware/'];
const types = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  let file = path.join(ROOT, decodeURIComponent(u.pathname));
  if (u.pathname === '/file') { file = path.resolve(u.searchParams.get('p')); if (!ALLOWED.some((a) => file.startsWith(a))) { res.writeHead(403); return res.end(); } }
  if (!fs.existsSync(file)) { res.writeHead(404); return res.end(); }
  if (file.endsWith('.stpZ')) { res.writeHead(200, { 'content-type': 'application/octet-stream' }); return fs.createReadStream(file).pipe(require('zlib').createGunzip()).pipe(res); }
  res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' }); fs.createReadStream(file).pipe(res);
}).listen(0, async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: +w, height: +h } });
  p.on('pageerror', (e) => console.error('pageerror', e.message));
  p.on('console', (m) => { if (m.type() === 'error') console.error('console', m.text()); });
  const qs = new URLSearchParams({ view, w, h, hide, glb: glbOut ? '1' : '' });
  await p.goto(`http://127.0.0.1:${server.address().port}/pcb3d.html?${qs}`);
  await p.waitForFunction(() => window.__done, null, { timeout: 900000 });
  await p.screenshot({ path: out });
  if (glbOut) fs.writeFileSync(glbOut, Buffer.from(await p.evaluate(() => window.__glb)));
  console.log(JSON.stringify(await p.evaluate(() => window.__info)));
  await b.close(); server.close();
});
