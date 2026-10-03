// Run with Node and sharp available through NODE_PATH. No Android build dependency.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'store/google-play/art');
fs.mkdirSync(out, { recursive: true });
const vector = fs.readFileSync(path.join(root, 'app/src/main/res/drawable/ic_launcher.xml'), 'utf8');
const paths = [...vector.matchAll(/<path android:fillColor="([^"]+)" android:pathData="([^"]+)"\s*\/>/g)]
  .map(m => `<path fill="${m[1]}" d="${m[2]}"/>`).join('\n');
const icon = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">${paths}</svg>`;
const feature = (lang) => {
  const tr = lang === 'tr-TR';
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
  <defs><linearGradient id="forest" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#254C3C"/><stop offset="1" stop-color="#10271D"/></linearGradient></defs>
  <rect width="1024" height="500" fill="url(#forest)"/>
  <g fill="none" stroke="#47735B" stroke-width="1" opacity=".5">
    <path d="M640 0V500M760 0V500M880 0V500M1000 0V500M580 90H1024M580 210H1024M580 330H1024M580 450H1024"/>
    <path d="M650 460L650 385L910 385L910 280L978 280" stroke="#9BC6A6" stroke-width="3"/>
  </g>
  <g fill="#9BC6A6"><circle cx="650" cy="460" r="5"/><circle cx="910" cy="385" r="5"/><circle cx="978" cy="280" r="5"/></g>
  <rect x="86" y="92" width="30" height="4" rx="2" fill="#E09159"/>
  <text x="86" y="124" font-family="Segoe UI, sans-serif" font-size="14" letter-spacing="3" fill="#9BC6A6">${tr ? 'LOJİSTİK YÖNETİM OYUNU' : 'LOGISTICS MANAGEMENT GAME'}</text>
  <text x="82" y="215" font-family="Segoe UI, sans-serif" font-weight="700" font-size="76" letter-spacing="-3" fill="#F4EFE5">Parcelrise</text>
  <text x="86" y="281" font-family="Segoe UI, sans-serif" font-weight="400" font-size="54" letter-spacing="1" fill="#E09159">Tycoon</text>
  <text x="86" y="358" font-family="Segoe UI, sans-serif" font-size="23" fill="#F4EFE5">${tr ? 'İlk teslimatından büyüyen bir şirkete.' : 'From first delivery to a growing company.'}</text>
  <path d="M86 392H340" stroke="#47735B" stroke-width="1"/>
  <g transform="translate(635 50) scale(3)">
    <ellipse cx="54" cy="93" rx="32" ry="7" fill="#0A1B13" opacity=".4"/>
    ${paths.replace(/<path fill="#1F3D30" d="M0,0h108v108h-108z"\/>/, '')}
  </g>
  </svg>`;
};
(async () => {
  fs.writeFileSync(path.join(out, 'icon.svg'), icon);
  await sharp(Buffer.from(icon)).ensureAlpha().png().toFile(path.join(out, 'icon-512.png'));
  for (const lang of ['en-US', 'tr-TR']) {
    const svg = feature(lang);
    fs.writeFileSync(path.join(out, `feature-${lang}.svg`), svg);
    await sharp(Buffer.from(svg)).flatten({background:'#1F3D30'}).removeAlpha().png().toFile(path.join(out, `feature-${lang}-1024x500.png`));
  }
  console.log('Exported matching icon and two localized feature graphics.');
})().catch(e => { console.error(e); process.exitCode = 1; });
