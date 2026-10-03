const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../store/google-play');
const release = JSON.parse(fs.readFileSync(path.join(root, 'release.json'), 'utf8'));
if (!/^[0-9a-f]{64}$/.test(release.sourceApkSha256)) throw new Error('Invalid source APK SHA-256');
const shots = [
  ['01-welcome', 'Başlangıç ekranı ve dil seçimi.', 'Welcome screen and language selection.'],
  ['02-dashboard', 'Şirket panelinde bakiye, oyun günü ve teslimat durumu.', 'Company dashboard with balance, game day and delivery status.'],
  ['03-jobs', 'İş pazarında rota, paket sayısı, toplam yük ve risk seçenekleri.', 'Job market showing routes, parcel counts, total loads and risks.'],
  ['04-cost-preview', 'Bir teslimatın beklenen ödemesi, gider rezervasyonu ve süresi.', 'A delivery preview showing expected payout, expense reservation and duration.'],
  ['05-fleet', 'Araç filosu ve kiralık panelvanın bilgileri.', 'Vehicle fleet and the rental van details.'],
  ['06-team', 'Şoför ekibi ve işe alım ekranına geçiş.', 'Driver team and access to hiring.'],
  ['07-map', 'Almanya temalı şematik merkez ve rota haritası.', 'Schematic map of German-inspired hubs and routes.'],
  ['08-company', 'Şirket seviyesi, hedefler ve bölge ilerlemesi.', 'Company stage, milestones and region progress.'],
];
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
(async () => {
  const assets = [];
  for (const locale of ['en-US','tr-TR']) {
    for (const [file, max] of [['title.txt',30],['short-description.txt',80],['full-description.txt',4000],['whats-new.txt',500]]) {
      const text = fs.readFileSync(path.join(root,locale,file),'utf8').trim();
      const count = [...text].length;
      if (!count || count > max || /\u00ad/.test(text)) throw new Error(`${locale}/${file}: invalid text length/character`);
      console.log(`${locale}/${file}: ${count}/${max} characters`);
    }
    for (const [name,tr,en] of shots) {
      const file = `${locale}/screenshots/${name}.png`;
      const full = path.join(root,file);
      let meta = await sharp(full).metadata();
      if (meta.width !== 1080 || meta.height !== 1920) throw new Error(`Wrong screenshot dimensions: ${file}`);
      if (meta.hasAlpha) {
        const rgb = await sharp(full).removeAlpha().png().toBuffer();
        fs.writeFileSync(full,rgb);
        meta = await sharp(full).metadata();
      }
      if (meta.channels !== 3 || meta.depth !== 'uchar') throw new Error(`Wrong screenshot format: ${file}`);
      if ([...tr].length > 140 || [...en].length > 140) throw new Error('Alt text too long');
      assets.push({file,kind:'actual-game-screenshot',locale,width:meta.width,height:meta.height,alt:{'tr-TR':tr,'en-US':en},sha256:hash(fs.readFileSync(full))});
    }
  }
  for (const file of ['art/icon-512.png','art/feature-en-US-1024x500.png','art/feature-tr-TR-1024x500.png']) {
    const full = path.join(root,file);
    const meta = await sharp(full).metadata();
    const isIcon = file.includes('icon');
    if (meta.width !== (isIcon?512:1024) || meta.height !== (isIcon?512:500) || meta.channels !== (isIcon?4:3)) throw new Error(`Wrong artwork format: ${file}`);
    if (isIcon && fs.statSync(full).size > 1024*1024) throw new Error('Icon too large');
    assets.push({file,kind:isIcon?'application-icon':'vector-promotion',width:meta.width,height:meta.height,alt:{'tr-TR':isIcon?'Yeşil zemin üzerinde bakır renkli paket ve yükseliş simgesi.':'Parcelrise Tycoon adı, paket amblemi ve şirketin büyümesini anlatan rota çizimi.','en-US':isIcon?'Copper parcel and rising chevron on a forest green background.':'Parcelrise Tycoon title, parcel emblem and a route illustrating company growth.'},sha256:hash(fs.readFileSync(full))});
  }
  fs.writeFileSync(path.join(root,'assets.json'),JSON.stringify({...release,assets},null,2)+'\n');
  console.log(`Validated ${assets.length} PNG assets; wrote assets.json.`);
})().catch(e=>{console.error(e);process.exitCode=1;});
