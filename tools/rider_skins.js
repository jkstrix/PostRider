// Rider roster and skin painter, loaded into Blockbench. Each skin starts from the base postman skin.
const H = s => [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
const mul = (c, f) => c.map(v => Math.max(0, Math.min(255, Math.round(v * f))));
const mix = (a, b, t) => a.map((v, i) => Math.round(v * (1 - t) + b[i] * t));
const hexOf = c => ((1 << 24) + (c[0] << 16) + (c[1] << 8) + c[2]).toString(16).slice(1);

window.__riders = [
  { id: 'chris', name: 'Chris', skin: 'D4A884', hair: ['C9A15F', 'A8834A'], style: 'short', eyes: '2A3A52', face: { mouth: 'grin' }, bandana: ['B3261E', 'F2F0EA', 'dots'] },
  { id: 'mattie', name: 'Martha "Mattie" Cole', she: true, skin: 'E8C3A0', hair: ['8E3B1E', '6E2C16'], style: 'braid', eyes: '3A6A3A', face: { freckles: true }, bandana: ['3E7B3A', 'E8E6D6', 'gingham'], vest: 'A07A4A' },
  { id: 'eli', name: 'Eli Whitaker', skin: 'C99E78', hair: ['A8A8A8', '8A8A8A'], style: 'short', eyes: '4A2E1A', face: { beard: 'full', beardColor: ['B0B0B0', '909090'], spectacles: 'C9A54A' }, bandana: ['1F2E57', '2A3C6E', 'plain'], vest: '2A2622', shirt: 'F0EEE6' },
  { id: 'rosa', name: 'Rosa Delgado', she: true, skin: 'B98660', hair: ['2A2420', '1C1814'], style: 'bun', eyes: '4A2E1A', face: {}, bandana: ['E07A2A', 'F2B24A', 'stripes'], rolled: true },
  { id: 'jeb', name: 'Jeb Stone', skin: 'D9A07E', hair: ['2A2420', '1C1814'], style: 'short', eyes: '4A2E1A', face: { beard: 'full', beardColor: ['2A2420', '3A302A'], bushy: true }, bandana: ['6B4A2E', '8A6440', 'plain'], patches: true },
  { id: 'tom', name: 'Tom Hale', skin: '6B4029', hair: ['1E1A18', '2A2420'], style: 'short', eyes: '3A2416', face: { beard: 'short', beardColor: ['1E1A18', '2A2420'] }, bandana: ['C9A227', 'E0BC48', 'plain'], vest: '5A5A5E' },
  { id: 'clara', name: 'Clara Bell', she: true, skin: 'E8C3A0', hair: ['E3C46A', 'C9A850'], style: 'pigtails', eyes: '3A5A8A', face: { freckles: true }, bandana: ['E07A9A', 'F6EEF0', 'dots'], shirt: 'F4F2EC' },
  { id: 'silas', name: 'Silas Crane', skin: 'E6CDB4', hair: ['1E1A18', '3A3A44'], style: 'slick', eyes: '5A6470', face: { moustache: 'pencil' }, bandana: ['1E1E22', '34343A', 'plain'], vest: '1E1C1A', shirt: '4A4A50' },
  { id: 'hattie', name: 'Hattie Mae Brooks', she: true, skin: 'C99E78', hair: ['A8A8A8', '8E8E8E'], style: 'bun', eyes: '4A2E1A', face: { laughLines: true }, bandana: ['9A82C0', 'EEE8F4', 'dots'], vest: 'A07A4A' },
  { id: 'wyatt', name: 'Wyatt Kincaid', skin: 'A87450', hair: ['553A22', '6B4A2E'], style: 'short', eyes: '5A6A80', face: { stubble: true, scar: true }, bandana: ['3E5F8A', '587AA4', 'plain'] },
  { id: 'ike', name: 'Ike Delaney', skin: 'E8C3A0', hair: ['B54A1E', '943A16'], style: 'short', eyes: '3A6A3A', face: { moustache: 'handlebar', beardColor: ['B54A1E', '943A16'], freckles: true }, bandana: ['3A7A3A', '4E944E', 'plain'] },
  { id: 'lottie', name: 'Lottie Park', she: true, skin: 'E3BC94', hair: ['1E1A18', '2C2622'], style: 'bob', eyes: '2A1E16', face: {}, bandana: ['2A8A8A', '3AA6A6', 'plain'] },
  { id: 'gus', name: 'Gus Tanner', skin: 'E0A895', hair: ['ECECEC', 'D0D0D0'], style: 'bald', eyes: '3A5A8A', face: { muttonchops: true, beardColor: ['ECECEC', 'D0D0D0'] }, bandana: ['E0C030', 'F2DC6A', 'plain'], vest: 'A07A4A' },
  { id: 'nell', name: '"One-Eyed" Nell Harper', she: true, skin: 'C99E78', hair: ['6B4A2E', '553A22'], style: 'long', eyes: '3A6A3A', face: { eyepatch: true }, bandana: ['B3261E', '1E1A18', 'check'], vest: '2A2622' },
  { id: 'abe', name: 'Abe Mercer', skin: '523222', hair: ['E6E6E6', 'C8C8C8'], style: 'short', eyes: '2A1A12', face: { beard: 'chinstrap', beardColor: ['BEBEBE', '9C9C9C'] }, bandana: ['6E1F2A', '8A2C38', 'plain'], vest: '5A5A5E' },
  { id: 'billy', name: 'Billy Sparrow', skin: 'E8C3A0', hair: ['6B4A2E', '553A22'], style: 'mop', eyes: '3A5A8A', face: { freckles: true, mouth: 'gap' }, bandana: ['6FA8DC', '9AC4EA', 'plain'], shirt: 'B8D0E8' },
  { id: 'ada', name: 'Ada Quill', she: true, skin: '8D5B3C', hair: ['1E1A18', '33291F'], style: 'curls', eyes: '2A1A12', face: { spectacles: 'B08A3A' }, bandana: ['EEE3C4', '2A4A8A', 'dots'], ink: true },
  { id: 'hank', name: 'Hank Morrow', skin: 'E0A07A', hair: ['E3C46A', 'C9A850'], style: 'short', eyes: '3A5A8A', face: { beard: 'full', beardColor: ['D8B85E', 'BFA04A'], sunburn: true }, bandana: ['6B7A3A', '849450', 'plain'] },
  { id: 'sadie', name: 'Sadie Fontaine', she: true, skin: 'E8C3A0', hair: ['B54A1E', '8E3414'], style: 'curls', eyes: '3A6A3A', face: { beautyMark: true }, bandana: ['C8202A', 'F2F0EA', 'stripes'], vest: '2A2622', shirt: 'F4F2EC' },
  { id: 'juan', name: 'Juan Herrera', skin: 'A87450', hair: ['1E1A18', '2C2622'], style: 'short', eyes: '3A2416', face: { moustache: 'horseshoe', beardColor: ['1E1A18', '2C2622'] }, bandana: ['C8202A', 'E8B030', 'serape'] }
];

window.__hairParts = { short: [], slick: [], mop: [], bald: [], bun: ['hair_bun'], braid: ['hair_braid'], pigtails: ['hair_pigtails'], bob: ['hair_back'], long: ['hair_back'], curls: ['hair_back'] };

window.__paintRider = spec => {
  const cv = document.createElement('canvas'); cv.width = 128; cv.height = 64;
  const g = cv.getContext('2d'); g.drawImage(window.__baseSkin, 0, 0);
  const img = g.getImageData(0, 0, 128, 64); const d = img.data;
  let seed = spec.id.split('').reduce((a, c) => (a * 31 + c.charCodeAt(0)) % 2147483647, 7) || 1;
  const rnd = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
  const set = (x, y, c) => { const i = (y * 128 + x) * 4; d[i] = c[0]; d[i + 1] = c[1]; d[i + 2] = c[2]; d[i + 3] = 255; };
  const get = (x, y) => { const i = (y * 128 + x) * 4; return [d[i], d[i + 1], d[i + 2]]; };
  const skin = H(spec.skin), shade = mul(skin, 0.84), cheek = mul(skin, 0.9), nose = mul(skin, 0.86), under = mul(skin, 0.92);
  const hair = H(spec.hair[0]), hairD = H(spec.hair[1]);
  // Hair is drawn in tidy strands: a darker strand every few pixels, offset row by row
  const strandAt = (x, y) => (x * 3 + y) % 5 === 0;
  const hairPix = (x, y) => set(x, y, strandAt(x, y) ? hairD : hair);
  const remap = (rects, map) => { for (const [x0, y0, w, h] of rects) for (let x = x0; x < x0 + w; x++) for (let y = y0; y < y0 + h; y++) { const k = hexOf(get(x, y)); if (map[k]) set(x, y, H(map[k])); } };

  // Head: skin all over, a little shadow at the jaw
  for (let x = 0; x < 32; x++) for (let y = 0; y < 16; y++) { if (y < 8 && (x < 8 || x >= 24)) continue; set(x, y, y < 8 && x >= 16 ? under : skin); }
  for (let x = 0; x < 8; x++) set(x, 15, shade);
  for (let x = 16; x < 24; x++) set(x, 15, shade);

  // Hair on top, the sides (right side 0..7 meets the face at x 7; left side 16..23 meets it at x 16) and the back
  const st = spec.style, bald = st === 'bald', long = ['bob', 'long', 'curls'].includes(st);
  const sideRows = { short: 3, slick: 3, mop: 5, bun: 4, braid: 4, pigtails: 4, bob: 7, long: 8, curls: 8 }[st] || 0;
  const backRows = { short: 6, slick: 6, mop: 7, bun: 7, braid: 8, pigtails: 7, bob: 8, long: 8, curls: 8 }[st] || 0;
  for (let x = 8; x < 16; x++) for (let y = 0; y < 8; y++) bald ? set(x, y, mix(skin, [255, 255, 255], 0.08)) : hairPix(x, y);
  for (let j = 0; j < 8; j++) for (let i = 0; i < 8; i++) {
    if (!bald) {
      // Long hair falls behind the ear, leaving the cheek clear near the face
      if (j < sideRows && !(long && j >= 3 && i >= 6)) hairPix(i, 8 + j);
      if (j < sideRows && !(long && j >= 3 && i <= 1)) hairPix(16 + i, 8 + j);
      // Sideburns
      if (!long && j >= 3 && j < 5 && i >= 6) hairPix(i, 8 + j);
      if (!long && j >= 3 && j < 5 && i <= 1) hairPix(16 + i, 8 + j);
      if (j < backRows) hairPix(24 + i, 8 + j);
    } else if (j === 3 || j === 4) {
      // A horseshoe fringe round the back
      if (i < 6) hairPix(i, 8 + j);
      if (i > 1) hairPix(16 + i, 8 + j);
      hairPix(24 + i, 8 + j);
    }
    if (st === 'slick' && j === 1) { set(i, 9, mix(hair, [255, 255, 255], 0.25)); set(16 + i, 9, mix(hair, [255, 255, 255], 0.25)); }
    if (st === 'curls' && (i + j) % 2 === 0) { if (j < sideRows) { if (!(j >= 3 && i >= 6)) set(i, 8 + j, hairD); if (!(j >= 3 && i <= 1)) set(16 + i, 8 + j, hairD); } set(24 + i, 8 + j, hairD); }
  }

  // Face, 8x8 at (8, 8): F(column, row)
  const F = (i, j, c) => set(8 + i, 8 + j, c);
  const fc = spec.face || {};
  const white = [0xD8, 0xD2, 0xC8], iris = H(spec.eyes);
  const lip = spec.she ? mix(skin, [0xB0, 0x40, 0x48], 0.5) : mul(skin, 0.72);
  const eyeRow = spec.she ? 4 : 3;
  for (let i = 0; i < 8; i++) { F(i, 0, bald ? shade : hair); F(i, 1, shade); F(i, 2, shade); }
  for (let i = 0; i < 8; i++) F(i, 7, cheek);
  if (spec.she) {
    // Hair frames the face down both sides, which narrows it; long hair all the way, tied-back hair to the ears
    const frameTo = long ? 7 : 4;
    for (let j = 1; j <= frameTo; j++) { F(0, j, j % 3 === 1 ? hairD : hair); F(7, j, j % 3 === 2 ? hairD : hair); }
    if (!long) for (let j = frameTo + 1; j < 8; j++) { F(0, j, mul(skin, 0.86)); F(7, j, mul(skin, 0.86)); }
    if (st === 'bob') for (let i = 1; i < 7; i++) if (i !== 3 && i !== 4) F(i, 1, strandAt(i, 1) ? hairD : hair);
    const browC = mix(hairD, skin, 0.3), lash = mul(hairD, 0.55);
    F(2, 2, browC); F(5, 2, browC);
    F(1, 3, lash); F(2, 3, mul(iris, 0.75)); F(5, 3, mul(iris, 0.75)); F(6, 3, lash); F(3, 3, skin); F(4, 3, skin);
    F(1, 4, white); F(2, 4, iris); F(5, 4, iris); F(6, 4, white); F(3, 4, skin); F(4, 4, skin);
    for (let i = 1; i < 7; i++) F(i, 5, skin);
    F(3, 5, mul(skin, 0.9)); F(4, 5, mul(skin, 0.9));
    F(1, 5, mix(skin, [0xE0, 0x70, 0x70], 0.22)); F(6, 5, mix(skin, [0xE0, 0x70, 0x70], 0.22));
    for (let i = 1; i < 7; i++) F(i, 6, skin);
    F(3, 6, lip); F(4, 6, lip);
    for (let i = 1; i < 7; i++) F(i, 7, i === 1 || i === 6 ? mul(skin, 0.9) : cheek);
    if (fc.freckles) for (const [i, j] of [[2, 5], [5, 5], [1, 6], [6, 6]]) F(i, j, mix(skin, [0x9A, 0x52, 0x2A], 0.45));
    if (fc.laughLines) { F(2, 6, mul(skin, 0.82)); F(5, 6, mul(skin, 0.82)); F(1, 3, mix(lash, skin, 0.3)); }
    if (fc.beautyMark) F(5, 5, [0x4A, 0x2A, 0x1A]);
  } else {
    if (!bald) { F(0, 1, hair); F(7, 1, hair); }
    if (st === 'mop' || st === 'bob') for (let i = 0; i < 8; i++) if (i !== 3 && i !== 4) F(i, 1, strandAt(i, 1) ? hairD : hair);
    const brow = mix(hairD, [0, 0, 0], 0.3);
    F(1, 2, brow); F(2, 2, brow); F(5, 2, brow); F(6, 2, brow);
    F(1, 3, white); F(2, 3, iris); F(5, 3, iris); F(6, 3, white);
    F(3, 4, nose); F(4, 4, nose);
    F(1, 5, cheek); F(6, 5, cheek);
    const tooth = [0xF0, 0xEC, 0xE0], dark = mul(skin, 0.55);
    if (fc.mouth === 'grin') { F(2, 6, dark); F(3, 6, tooth); F(4, 6, tooth); F(5, 6, dark); }
    else if (fc.mouth === 'gap') { F(2, 6, dark); F(3, 6, tooth); F(4, 6, mul(skin, 0.4)); F(5, 6, tooth); F(6, 6, dark); }
    else { F(3, 6, lip); F(4, 6, lip); }
    if (fc.freckles) for (const [i, j] of [[1, 4], [2, 5], [5, 5], [6, 4], [0, 4], [7, 5]]) F(i, j, mix(skin, [0x9A, 0x52, 0x2A], 0.45));
    if (fc.sunburn) for (const [i, j] of [[1, 4], [2, 4], [5, 4], [6, 4], [3, 3], [4, 3]]) F(i, j, mix(skin, [0xD0, 0x50, 0x40], 0.35));
    if (fc.laughLines) { F(1, 6, mul(skin, 0.8)); F(6, 6, mul(skin, 0.8)); F(0, 3, mul(skin, 0.82)); F(7, 3, mul(skin, 0.82)); }
    if (fc.beautyMark) F(5, 5, [0x4A, 0x2A, 0x1A]);
    if (fc.scar) { const sc = mix(skin, [0xF0, 0xC8, 0xC0], 0.55); F(5, 4, sc); F(6, 5, sc); F(6, 6, sc); }
    if (fc.stubble) for (let j = 5; j < 8; j++) for (let i = 0; i < 8; i++) if (rnd() < 0.45 && !(j === 6 && (i === 3 || i === 4))) F(i, j, mix(skin, hairD, 0.35));
  }

  // Facial hair
  const bc = fc.beardColor ? [H(fc.beardColor[0]), H(fc.beardColor[1])] : [hair, hairD];
  const bPix = (x, y) => set(x, y, strandAt(x, y) ? bc[1] : bc[0]);
  const B = (i, j) => bPix(8 + i, 8 + j);
  const sides = (rows, cols) => { for (const j of rows) for (const c of cols) { bPix(7 - c, 8 + j); bPix(16 + c, 8 + j); } };
  if (fc.beard === 'full') {
    for (let i = 1; i < 7; i++) B(i, 5);
    for (let i = 0; i < 8; i++) { B(i, 6); B(i, 7); }
    F(3, 6, lip); F(4, 6, lip);
    if (fc.bushy) { B(0, 5); B(7, 5); B(0, 4); B(7, 4); }
    sides([4, 5, 6, 7], [0, 1, 2, 3]);
    for (let x = 16; x < 24; x++) for (let y = 4; y < 8; y++) bPix(x, y);
  }
  if (fc.beard === 'short') { for (let i = 2; i < 6; i++) B(i, 5); for (let i = 0; i < 8; i++) B(i, 7); B(0, 6); B(7, 6); B(1, 6); B(6, 6); sides([6, 7], [0, 1]); }
  if (fc.beard === 'chinstrap') { for (let i = 0; i < 8; i++) B(i, 7); for (const j of [4, 5, 6]) { B(0, j); B(7, j); } sides([4, 5, 6, 7], [0, 1]); }
  if (fc.beard === 'goatee') { for (let i = 2; i < 6; i++) B(i, 7); B(2, 6); B(5, 6); }
  if (fc.moustache === 'pencil') for (let i = 2; i < 6; i++) F(i, 5, mix(skin, bc[0], 0.75));
  if (fc.moustache === 'handlebar') { for (let i = 1; i < 7; i++) B(i, 5); B(0, 4); B(7, 4); }
  if (fc.moustache === 'horseshoe') { for (let i = 1; i < 7; i++) B(i, 5); B(1, 6); B(6, 6); B(1, 7); B(6, 7); }
  if (fc.muttonchops) { for (let j = 3; j < 8; j++) { B(0, j); B(7, j); } B(1, 5); B(6, 5); B(1, 6); B(6, 6); for (let i = 2; i < 6; i++) B(i, 5); sides([3, 4, 5, 6, 7], [0, 1]); }
  if (fc.spectacles) {
    const fr = H(fc.spectacles), lens = [0xE4, 0xEE, 0xF0];
    F(1, eyeRow, lens); F(6, eyeRow, lens); F(0, eyeRow, fr); F(3, eyeRow, fr); F(4, eyeRow, fr); F(7, eyeRow, fr);
    for (let i = 0; i < 4; i++) { set(7 - i, 8 + eyeRow, fr); set(16 + i, 8 + eyeRow, fr); }
  }
  if (fc.eyepatch) {
    const p = [0x1A, 0x16, 0x14];
    for (const [i, j] of [[5, eyeRow - 1], [6, eyeRow - 1], [5, eyeRow], [6, eyeRow], [5, eyeRow + 1], [6, eyeRow + 1], [4, 1], [3, 1], [2, 1], [1, 1], [7, 2]]) F(i, j, p);
    for (let i = 0; i < 8; i++) { set(16 + i, i < 4 ? 10 : 9, p); set(i, 9, p); set(24 + i, 9, p); }
  }

  // Long-hair parts (y 32..48): back panel, bun, braid with a ribbon, pigtails with ribbons
  const paintBox = (u, v, w, h, dd, fn) => { for (let x = u; x < u + 2 * (dd + w); x++) for (let y = v; y < v + dd + h; y++) set(x, y, fn(x - u, y - v)); };
  const strand = (x, y) => (st === 'curls' ? (x + y) % 2 === 0 : x % 3 === 0) ? hairD : hair;
  const wears = window.__hairParts[st] || [];
  for (let x = 0; x < 48; x++) for (let y = 32; y < 48; y++) d[(y * 128 + x) * 4 + 3] = 0;
  if (wears.includes('hair_back')) paintBox(0, 32, 9, 8, 1, strand);
  if (wears.includes('hair_bun')) paintBox(20, 32, 3, 3, 2, (x, y) => (x + y) % 3 === 0 ? hairD : hair);
  if (wears.includes('hair_braid')) paintBox(30, 32, 2, 9, 1, (x, y) => y === 7 ? H(spec.bandana[0]) : ((x + y) % 2 === 0 ? hairD : hair));
  if (wears.includes('hair_pigtails')) paintBox(36, 32, 1, 5, 2, (x, y) => y === 2 ? H(spec.bandana[0]) : strand(x, y));

  // Bandana: the collar (64..92, 28..35) and knot (92..100, 28..31)
  const [bb, ba, pat] = [H(spec.bandana[0]), H(spec.bandana[1]), spec.bandana[2]];
  const serape = ['C8202A', 'E07A2A', 'E8B030', '3A7A3A', '2A5A9A', 'F2F0EA'].map(H);
  const bandPix = (x, y) => {
    if (pat === 'dots') return ((x + 2 * y) % 4 === 0 && y % 2 === 0) ? ba : bb;
    if (pat === 'gingham') return (x % 2 === 0 && y % 2 === 0) ? ba : ((x % 2 === 0 || y % 2 === 0) ? mix(bb, ba, 0.45) : bb);
    if (pat === 'check') return ((Math.floor(x / 2) + Math.floor(y / 2)) % 2 === 0) ? ba : bb;
    if (pat === 'stripes') return x % 3 === 0 ? ba : bb;
    if (pat === 'serape') return serape[x % serape.length];
    return rnd() < 0.2 ? mul(bb, 0.9) : bb;
  };
  for (let x = 64; x < 100; x++) for (let y = 28; y < 35; y++) {
    const i = (y * 128 + x) * 4;
    if (!d[i + 3]) continue;
    set(x, y, mul(bandPix(x, y), y < 33 && x < 92 ? 0.92 : 1));
  }

  // Outfit: vest colour, shirt colour, patches, rolled sleeves, inky cuffs
  if (spec.vest) { const V = H(spec.vest); remap([[16, 16, 24, 16]], { '6b4428': hexOf(V), '573620': hexOf(mul(V, 0.82)), '7c5232': hexOf(mul(V, 1.15)), '8a5c30': hexOf(mul(V, 1.25)), 'a8763f': hexOf(mul(V, 1.45)) }); }
  if (spec.shirt) { const S = H(spec.shirt); remap([[16, 16, 24, 16], [40, 16, 16, 16], [32, 48, 16, 16]], { 'e6dac2': hexOf(S), 'd9cbb0': hexOf(mul(S, 0.94)), 'c4b596': hexOf(mul(S, 0.85)) }); }
  if (spec.patches) for (const [x, y, c] of [[21, 22, 'A88A5A'], [21, 23, 'A88A5A'], [27, 25, '4A6A8A'], [26, 25, '4A6A8A'], [33, 23, 'A88A5A']]) set(x, y, H(c));
  if (spec.rolled) for (const [x0, y0] of [[40, 16], [32, 48]]) for (let x = x0; x < x0 + 16; x++) { set(x, y0 + 8, mul(get(x, y0 + 4), 0.95)); set(x, y0 + 9, shade); for (let y = y0 + 10; y < y0 + 12; y++) set(x, y, skin); }
  if (spec.ink) for (const [x0, y0] of [[40, 16], [32, 48]]) for (let x = x0; x < x0 + 16; x += 3) set(x, y0 + 11, H('2A3A6A'));

  g.putImageData(img, 0, 0);
  return cv;
};

// Slim (3-wide) arms: each arm's box UV loses a column from the faces across its width
window.__slimArms = cv => {
  const copy = document.createElement('canvas'); copy.width = 128; copy.height = 64; copy.getContext('2d').drawImage(cv, 0, 0);
  const g = cv.getContext('2d');
  for (const [u, v] of [[40, 16], [32, 48]]) {
    g.clearRect(u, v, 16, 16);
    const put = (sx, sy, w, h, dx, dy) => g.drawImage(copy, sx, sy, w, h, dx, dy, w, h);
    put(u + 4, v, 3, 4, u + 4, v);
    put(u + 8, v, 3, 4, u + 7, v);
    put(u, v + 4, 4, 12, u, v + 4);
    put(u + 4, v + 4, 3, 12, u + 4, v + 4);
    put(u + 8, v + 4, 4, 12, u + 7, v + 4);
    put(u + 12, v + 4, 3, 12, u + 11, v + 4);
  }
  return cv;
};
window.__isSlim = r => r.slim !== undefined ? r.slim : !!r.she;

// Narrow (7-wide) head: the faces across its width lose their middle column (column 4), keeping the face symmetrical
window.__slimHead = cv => {
  const copy = document.createElement('canvas'); copy.width = 128; copy.height = 64; copy.getContext('2d').drawImage(cv, 0, 0);
  const g = cv.getContext('2d');
  const drop = (sx, sy, w, h, dx, dy, col) => { g.drawImage(copy, sx, sy, col, h, dx, dy, col, h); g.drawImage(copy, sx + col + 1, sy, w - col - 1, h, dx + col, dy, w - col - 1, h); };
  g.clearRect(0, 0, 32, 16);
  drop(8, 0, 8, 8, 8, 0, 4);
  drop(16, 0, 8, 8, 15, 0, 4);
  g.drawImage(copy, 0, 8, 8, 8, 0, 8, 8, 8);
  drop(8, 8, 8, 8, 8, 8, 4);
  g.drawImage(copy, 16, 8, 8, 8, 15, 8, 8, 8);
  drop(24, 8, 8, 8, 23, 8, 4);
  g.clearRect(0, 32, 20, 9);
  drop(1, 32, 9, 1, 1, 32, 4);
  drop(10, 32, 9, 1, 9, 32, 4);
  g.drawImage(copy, 0, 33, 1, 8, 0, 33, 1, 8);
  drop(1, 33, 9, 8, 1, 33, 4);
  g.drawImage(copy, 10, 33, 1, 8, 9, 33, 1, 8);
  drop(11, 33, 9, 8, 10, 33, 4);
  return cv;
};

const slimProject = Project.name.startsWith('postman_slim');
for (const t of Texture.all.filter(t => t.name.startsWith('rider_'))) t.remove();
for (const r of window.__riders.filter(r => window.__isSlim(r) === slimProject)) {
  let cv = window.__paintRider(r);
  if (slimProject) cv = window.__slimHead(window.__slimArms(cv));
  const old = Texture.all.find(t => t.name === 'rider_' + r.id);
  if (old) old.remove();
  new Texture({ name: 'rider_' + r.id }).fromDataURL(cv.toDataURL()).add(false);
}
return Texture.all.length;
