// The mod icon: a postage stamp with Chris on it. Renders him from the postman project, then draws the stamp round him.
const req = arguments[0], out = arguments[1];
ModelProject.all.find(p => p.name === 'postman').select();
const pv = Preview.all.find(p => p.id === 'mcp_offscreen_pm');
const showGroup = (name, v) => { const gr = Group.all.find(x => x.name === name); if (!gr) return; gr.visibility = v; gr.children.forEach(c => { c.visibility = v; if (c.children) c.children.forEach(cc => cc.visibility = v); }); };
for (const p of window.__props) showGroup(p, false);
for (const h of ['hair_back', 'hair_bun', 'hair_braid', 'hair_pigtails']) showGroup(h, false);
const tex = Texture.all.find(t => t.name === 'rider_chris');
tex.select(); Cube.all.forEach(c => c.applyTexture(tex, true));
Animation.all.find(a => a.name === 'tip_hat').select(); __ponyAt(0.7);
Canvas.updateAll(); Canvas.updateVisibility();
const bg = pv.renderer.getClearColor(new THREE.Color()).clone(), bgAlpha = pv.renderer.getClearAlpha();
pv.renderer.setClearColor(0x000000, 0);
pv.camera.position.set(-22, 27, -34); pv.controls.target.set(-1, 21.5, 0); pv.camera.lookAt(-1, 21.5, 0); pv.controls.update(); pv.render();
const S = 256, g0 = document.createElement('canvas'); g0.width = S; g0.height = S;
const g = g0.getContext('2d'); g.imageSmoothingEnabled = false;
// Perforated stamp paper
g.fillStyle = '#F2E9CE'; g.fillRect(10, 10, S - 20, S - 20);
g.globalCompositeOperation = 'destination-out';
for (let i = 14; i < S - 10; i += 16) for (const [x, y] of [[i, 10], [i, S - 10], [10, i], [S - 10, i]]) { g.beginPath(); g.arc(x, y, 5, 0, Math.PI * 2); g.fill(); }
g.globalCompositeOperation = 'source-over';
// Red frame and the picture: a prairie sky with low hills
g.fillStyle = '#8E2A1E'; g.fillRect(24, 24, S - 48, S - 48);
const px = 30, py = 30, pw = S - 60, ph = S - 96;
const sky = g.createLinearGradient(0, py, 0, py + ph); sky.addColorStop(0, '#9CC7E4'); sky.addColorStop(1, '#F4D9A4');
g.fillStyle = sky; g.fillRect(px, py, pw, ph);
g.fillStyle = '#C9A15F'; g.beginPath(); g.moveTo(px, py + ph * 0.78);
for (let x = 0; x <= pw; x += 8) g.lineTo(px + x, py + ph * 0.78 - Math.sin(x / pw * Math.PI * 2.2) * 7);
g.lineTo(px + pw, py + ph); g.lineTo(px, py + ph); g.fill();
g.fillStyle = '#A9824A'; g.fillRect(px, py + ph - 14, pw, 14);
// Chris
const src = pv.canvas, s = Math.min(src.width, src.height);
g.save(); g.beginPath(); g.rect(px, py, pw, ph); g.clip();
g.drawImage(src, (src.width - s) / 2, (src.height - s) / 2, s, s, px - 6, py - 4, pw + 12, pw + 12);
g.restore();
// The value in the corner and the name along the bottom, in the barge's lettering
const FONT = { P: ['111','101','111','100','100'], O: ['111','101','101','101','111'], S: ['011','100','010','001','110'], T: ['111','010','010','010','010'], R: ['110','101','110','101','101'], I: ['111','010','010','010','111'], D: ['110','101','101','101','110'], E: ['111','100','110','100','111'], '2': ['111','001','111','100','111'], 'c': ['000','011','100','100','011'], ' ': ['000','000','000','000','000'] };
const letter = (text, x, y, scale, col) => { g.fillStyle = col; [...text].forEach((ch, n) => FONT[ch].forEach((row, r) => [...row].forEach((bit, c) => { if (bit === '1') g.fillRect(x + (n * 4 + c) * scale, y + r * scale, scale, scale); }))); };
g.fillStyle = '#F2E9CE'; g.fillRect(px, py + ph + 6, pw, S - 30 - (py + ph + 6));
const name = 'POST RIDER', scale = 4, w = (name.length * 4 - 1) * scale;
letter(name, (S - w) / 2, py + ph + 13, scale, '#8E2A1E');
g.fillStyle = 'rgba(242,233,206,0.9)'; g.fillRect(px + pw - 34, py + 4, 30, 22);
letter('2c', px + pw - 30, py + 8, 3, '#8E2A1E');
pv.renderer.setClearColor(bg, bgAlpha);
const base = Texture.all.find(t => t.name === 'postman');
base.select(); Cube.all.forEach(c => c.applyTexture(base, true));
for (const p of window.__props) showGroup(p, true);
for (const h of ['hair_back', 'hair_bun', 'hair_braid', 'hair_pigtails']) showGroup(h, true);
Canvas.updateAll(); Canvas.updateVisibility();
req('fs').writeFileSync(out, Buffer.from(g0.toDataURL('image/png').split(',')[1], 'base64'));
return out;
