// The five table rooms. Rooms.scene(roomId, view) draws a full 1200 x 760 table from live game state.
(function () {
  const F = {
    bodoni: "'Bodoni Moda', Didot, Georgia, serif",
    sans: "'Archivo', system-ui, sans-serif",
    corm: "'Cormorant Garamond', Garamond, Georgia, serif",
    lime: "'Limelight', Georgia, serif",
    mono: "'JetBrains Mono', ui-monospace, Consolas, monospace",
    noto: "'Noto Serif TC', 'Songti TC', 'PMingLiU', serif"
  };
  let uid = 0;

  function esc(s) {
    return String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  }
  function T(x, y, s, o = {}) {
    return `<text x="${x}" y="${y}" text-anchor="${o.a || 'middle'}" font-family="${o.f}" font-size="${o.s || 16}" font-weight="${o.w || 400}" fill="${o.c || '#fff'}"${o.ls ? ` letter-spacing="${o.ls}"` : ''}${o.i ? ' font-style="italic"' : ''}>${esc(s)}</text>`;
  }
  function nest(cardSvg, w) {
    const h = w * 1.4;
    return cardSvg.replace('<svg class="card"', `<svg x="${-w / 2}" y="${-h / 2}" width="${w}" height="${h}"`);
  }
  function place(cardSvg, cx, cy, w, rot, filter) {
    return `<g transform="translate(${cx} ${cy}) rotate(${rot || 0})"${filter ? ` filter="url(#${filter})"` : ''}>${nest(cardSvg, w)}</g>`;
  }
  const oct = (x1, y1, x2, y2, c) => `${x1 + c},${y1} ${x2 - c},${y1} ${x2},${y1 + c} ${x2},${y2 - c} ${x2 - c},${y2} ${x1 + c},${y2} ${x1},${y2 - c} ${x1},${y1 + c}`;
  const vignette = (k, strength) => `<defs><radialGradient id="vg${k}" cx="50%" cy="48%" r="72%"><stop offset=".5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="${strength}"/></radialGradient></defs><rect width="1200" height="760" fill="url(#vg${k})"/>`;

  const ROOMS = {
    'gold-room': {
      name: 'The Gold Room', deck: 'noir', accent: '#C9A24B', edge: '#08090A',
      blurb: 'Private casino room. Black leather rail, emerald felt, gold trim.',
      floor(k) {
        return `<defs><radialGradient id="lt${k}" cx="50%" cy="46%" r="62%"><stop offset="0" stop-color="#2A2B2E"/><stop offset="1" stop-color="#08090A"/></radialGradient><pattern id="cp${k}" width="28" height="28" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="28" height="28" fill="none" stroke="#C9A24B" stroke-opacity=".05"/></pattern></defs><rect width="1200" height="760" fill="url(#lt${k})"/><rect width="1200" height="760" fill="url(#cp${k})"/>`;
      },
      table(k) {
        return `<defs><radialGradient id="fe${k}" cx="50%" cy="45%" r="62%"><stop offset="0" stop-color="#15654E"/><stop offset="1" stop-color="#0A3328"/></radialGradient></defs>
          <rect x="184" y="156" width="832" height="430" rx="215" fill="#000" opacity=".6" filter="url(#bl${k})"/>
          <rect x="190" y="150" width="820" height="420" rx="210" fill="#17181B" stroke="#2C2E33" stroke-width="2"/>
          <rect x="203" y="163" width="794" height="394" rx="197" fill="none" stroke="#C9A24B" stroke-opacity=".55" stroke-dasharray="6 5"/>
          <rect x="226" y="186" width="748" height="348" rx="174" fill="url(#fe${k})" stroke="#050606" stroke-width="3"/>
          <rect x="278" y="226" width="644" height="268" rx="134" fill="none" stroke="#C9A24B" stroke-opacity=".25"/>`;
      },
      plate(x, y, name, n, active) {
        let s = '';
        if (active) s += `<circle cx="${x}" cy="${y}" r="50" fill="#C9A24B" opacity=".1"/><circle cx="${x}" cy="${y}" r="42" fill="#C9A24B" opacity=".14"/>`;
        s += `<circle cx="${x}" cy="${y}" r="34" fill="#1E2126" stroke="${active ? '#C9A24B' : '#3A3D44'}" stroke-width="${active ? 3 : 1.5}"/>`;
        s += T(x, y + 11, name[0].toUpperCase(), { f: F.bodoni, s: 30, w: 700, c: '#ECE6D6' });
        s += T(x, y + 58, name, { f: F.sans, s: 16, w: 600, c: active ? '#C9A24B' : '#ECE6D6' });
        if (n != null) s += `<rect x="${x + 24}" y="${y - 44}" width="40" height="24" rx="12" fill="#0D0E10" stroke="#C9A24B" stroke-width="1.2"/>` + T(x + 44, y - 27, n, { f: F.bodoni, s: 15, w: 700, c: '#ECE6D6' });
        return s;
      },
      button(x, y, label, primary) {
        return `<rect x="${x - 75}" y="${y - 22}" width="150" height="44" rx="8" fill="${primary ? '#C9A24B' : '#22252A'}" stroke="${primary ? '#8D7134' : '#3A3D44'}"/>` + T(x, y + 6, label, { f: F.sans, s: 16, w: 700, c: primary ? '#111215' : '#ECE6D6' });
      },
      tableText(x, y, t) { return T(x, y, t, { f: F.bodoni, s: 22, i: 1, w: 500, c: '#E6E0CF' }); },
      status(x, y, t) { return `<rect x="${x - 185}" y="${y - 19}" width="370" height="38" rx="19" fill="#000" fill-opacity=".45" stroke="#C9A24B" stroke-opacity=".45"/>` + T(x, y + 6, t, { f: F.sans, s: 15, c: '#ECE6D6' }); }
    },

    library: {
      name: 'The Library', deck: 'classic', accent: '#F3D9A0', edge: '#2A190E',
      blurb: 'Members’ club. Oak parquet, burgundy rug, mahogany and brass.',
      floor(k) {
        let fringe = '';
        for (let y = 110; y <= 610; y += 9) fringe += `<path d="M142 ${y}H150M1050 ${y}H1058"/>`;
        return `<defs><pattern id="hb${k}" width="48" height="48" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="48" height="24" fill="#5A3A22" stroke="#3A2414"/><rect y="24" width="24" height="24" fill="#4E321D" stroke="#3A2414"/><rect x="24" y="24" width="24" height="24" fill="#563720" stroke="#3A2414"/></pattern></defs>
          <rect width="1200" height="760" fill="url(#hb${k})"/>
          <rect x="150" y="104" width="900" height="512" fill="#5E1A1E"/>
          <rect x="164" y="118" width="872" height="484" fill="none" stroke="#D9B98A" stroke-width="3"/>
          <rect x="176" y="130" width="848" height="460" fill="none" stroke="#D9B98A" stroke-opacity=".45" stroke-dasharray="2 6"/>
          <rect x="188" y="142" width="824" height="436" fill="none" stroke="#2E5A4A" stroke-width="6"/>
          <g stroke="#D9B98A" stroke-opacity=".7">${fringe}</g>` + vignette(k, .65);
      },
      table(k) {
        let studs = '';
        for (let i = 0; i < 56; i++) { const a = i / 56 * 2 * Math.PI; studs += `<circle cx="${(600 + Math.cos(a) * 396).toFixed(1)}" cy="${(360 + Math.sin(a) * 196).toFixed(1)}" r="2.6" fill="#D2AE68"/>`; }
        return `<defs><radialGradient id="gf${k}" cx="50%" cy="45%" r="62%"><stop offset="0" stop-color="#2A6448"/><stop offset="1" stop-color="#163826"/></radialGradient></defs>
          <ellipse cx="600" cy="374" rx="420" ry="218" fill="#000" opacity=".55" filter="url(#bl${k})"/>
          <ellipse cx="600" cy="360" rx="412" ry="212" fill="#4A2616" stroke="#2B140B" stroke-width="3"/>
          <ellipse cx="600" cy="360" rx="404" ry="204" fill="none" stroke="#6B3A22" stroke-opacity=".6"/>
          <ellipse cx="600" cy="354" rx="388" ry="194" fill="none" stroke="#7A4528" stroke-opacity=".35" stroke-width="3"/>
          ${studs}
          <ellipse cx="600" cy="360" rx="372" ry="176" fill="url(#gf${k})" stroke="#1E0E06" stroke-width="3"/>
          <circle cx="1110" cy="150" r="27" fill="#3B2416" stroke="#C29A57" stroke-width="1.5"/>
          <circle cx="1110" cy="150" r="17" fill="#fff" fill-opacity=".12" stroke="#fff" stroke-opacity=".45"/>
          <circle cx="1110" cy="150" r="12" fill="#B5651D" fill-opacity=".75"/>
          <circle cx="1104" cy="144" r="3" fill="#fff" fill-opacity=".5"/>`;
      },
      plate(x, y, name, n, active) {
        const w = 150; let s = '';
        if (active) s += `<rect x="${x - w / 2 - 6}" y="${y - 28}" width="${w + 12}" height="56" rx="7" fill="none" stroke="#F3D9A0" stroke-width="2"/>`;
        s += `<rect x="${x - w / 2}" y="${y - 22}" width="${w}" height="44" rx="4" fill="#C29A57" stroke="#6E5428" stroke-width="1.5"/><rect x="${x - w / 2 + 4}" y="${y - 18}" width="${w - 8}" height="36" rx="2" fill="none" stroke="#E9CC8E" stroke-opacity=".7"/><circle cx="${x - w / 2 + 10}" cy="${y}" r="2.5" fill="#6E5428"/><circle cx="${x + w / 2 - 10}" cy="${y}" r="2.5" fill="#6E5428"/>`;
        s += T(x, y + 8, name, { f: F.corm, s: 24, w: 700, c: '#2E200E' });
        if (n != null) s += T(x, y + 48, `${n} cards${active ? ' · to play' : ''}`, { f: F.corm, s: 18, w: 700, i: 1, c: active ? '#F3D9A0' : '#E8D9B8' });
        return s;
      },
      button(x, y, label, primary) {
        return `<rect x="${x - 75}" y="${y - 22}" width="150" height="44" rx="5" fill="${primary ? '#7A1C22' : '#EFE6D2'}" stroke="${primary ? '#4A0F13' : '#B08D57'}" stroke-width="1.5"/>` + T(x, y + 7, label, { f: F.corm, s: 22, w: 700, c: primary ? '#F3EAD2' : '#3A2A14' });
      },
      tableText(x, y, t) { return T(x, y, t, { f: F.corm, s: 27, w: 700, i: 1, c: '#F3EAD2' }); },
      status(x, y, t) { return `<rect x="${x - 185}" y="${y - 19}" width="370" height="38" rx="3" fill="#EFE6D2" stroke="#B08D57"/>` + T(x, y + 6, t, { f: F.corm, s: 19, w: 700, c: '#3A2A14' }); }
    },

    salon: {
      name: 'Salon Privé', deck: 'deco', accent: '#B8913A', edge: '#0B1120',
      blurb: '1920s Riviera. Navy marble, an octagonal velvet table, gold inlay.',
      floor(k) {
        return `<defs><pattern id="ck${k}" width="64" height="64" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="64" height="64" fill="#141C2C"/><rect width="32" height="32" fill="#1D2740"/><rect x="32" y="32" width="32" height="32" fill="#1D2740"/><path d="M0 0H64M0 0V64M32 0V64M0 32H64" stroke="#B8913A" stroke-opacity=".16"/></pattern></defs><rect width="1200" height="760" fill="url(#ck${k})"/>` + vignette(k, .7);
      },
      table(k) {
        let rays = '';
        for (let i = 0; i < 36; i++) { const a = i * 10 * Math.PI / 180; rays += `<line x1="${(600 + Math.cos(a) * 70).toFixed(1)}" y1="${(360 + Math.sin(a) * 70).toFixed(1)}" x2="${(600 + Math.cos(a) * 150).toFixed(1)}" y2="${(360 + Math.sin(a) * 150).toFixed(1)}"/>`; }
        const dia = (x, y) => `<rect x="${x - 6}" y="${y - 6}" width="12" height="12" transform="rotate(45 ${x} ${y})" fill="#B8913A"/>`;
        return `<defs><radialGradient id="vv${k}" cx="50%" cy="46%" r="62%"><stop offset="0" stop-color="#2A3E66"/><stop offset="1" stop-color="#162238"/></radialGradient></defs>
          <polygon points="${oct(184, 160, 1016, 584, 100)}" fill="#000" opacity=".6" filter="url(#bl${k})"/>
          <polygon points="${oct(190, 150, 1010, 570, 100)}" fill="#0E1524" stroke="#B8913A" stroke-width="3"/>
          <polygon points="${oct(200, 160, 1000, 560, 96)}" fill="none" stroke="#B8913A" stroke-opacity=".5"/>
          <polygon points="${oct(214, 174, 986, 546, 90)}" fill="url(#vv${k})"/>
          <polygon points="${oct(250, 206, 950, 514, 76)}" fill="none" stroke="#B8913A" stroke-opacity=".4"/>
          <g stroke="#B8913A" stroke-opacity=".14">${rays}</g>
          <circle cx="600" cy="360" r="70" fill="none" stroke="#B8913A" stroke-opacity=".18"/><circle cx="600" cy="360" r="150" fill="none" stroke="#B8913A" stroke-opacity=".14"/>
          ${dia(600, 206)}${dia(600, 514)}${dia(250, 360)}${dia(950, 360)}`;
      },
      plate(x, y, name, n, active) {
        let s = `<polygon points="${oct(x - 80, y - 25, x + 80, y + 25, 10)}" fill="${active ? '#B8913A' : '#0E1524'}" stroke="#B8913A" stroke-width="1.5"/>`;
        if (!active) s += `<polygon points="${oct(x - 75, y - 20, x + 75, y + 20, 8)}" fill="none" stroke="#B8913A" stroke-opacity=".4"/>`;
        s += T(x, y + 7, name, { f: F.lime, s: 20, c: active ? '#0E1524' : '#E9D9AE' });
        if (n != null) s += T(x, y + 46, `${n} CARDS`, { f: F.sans, s: 12, w: 600, ls: 2, c: active ? '#E9D9AE' : '#A9B3C7' });
        return s;
      },
      button(x, y, label, primary) {
        return `<polygon points="${oct(x - 75, y - 22, x + 75, y + 22, 10)}" fill="${primary ? '#B8913A' : '#0E1524'}" stroke="#B8913A" stroke-width="1.5"/>` + T(x, y + 7, label.toUpperCase(), { f: F.lime, s: 18, ls: 2, c: primary ? '#0E1524' : '#E9D9AE' });
      },
      tableText(x, y, t) { return T(x, y, t, { f: F.lime, s: 21, ls: 1, c: '#E9D9AE' }); },
      status(x, y, t) { return `<polygon points="${oct(x - 185, y - 19, x + 185, y + 19, 10)}" fill="#0E1524" fill-opacity=".88" stroke="#B8913A" stroke-opacity=".7"/>` + T(x, y + 5, t, { f: F.sans, s: 15, c: '#E9D9AE' }); }
    },

    'tea-house': {
      name: 'Tea House', deck: 'teahouse', accent: '#E7C27D', edge: '#1A0F09',
      blurb: 'Hong Kong back room. Square rosewood table, jade baize, tea at every corner.',
      pos: { left: [196, 320], right: [1000, 320], top: [570, 70], backsTop: [790, 70], backsLeft: [214, 440], backsRight: [1018, 440] },
      floor(k) {
        return `<defs><pattern id="pl${k}" width="300" height="38" patternUnits="userSpaceOnUse"><rect width="300" height="38" fill="#2B1A11"/><path d="M0 37H300M190 0V38" stroke="#170D08" stroke-width="2"/><path d="M20 12C90 8 150 16 260 11" stroke="#3A2418" fill="none"/></pattern><radialGradient id="ln${k}"><stop offset="0" stop-color="#E0402F" stop-opacity=".32"/><stop offset="1" stop-color="#E0402F" stop-opacity="0"/></radialGradient><pattern id="fr${k}" width="22" height="22" patternUnits="userSpaceOnUse"><rect width="22" height="22" fill="#3B1F14"/><path d="M3 3H19V19H8V8H14V14" fill="none" stroke="#7A4A2E" stroke-width="1.6"/></pattern></defs>
          <rect width="1200" height="760" fill="url(#pl${k})"/>
          <circle cx="60" cy="60" r="280" fill="url(#ln${k})"/><circle cx="1140" cy="60" r="280" fill="url(#ln${k})"/>` + vignette(k, .55) +
          `<path d="M0 0H1200V760H0Z M22 22V738H1178V22Z" fill="url(#fr${k})" fill-rule="evenodd"/><rect x="22" y="22" width="1156" height="716" fill="none" stroke="#170D08" stroke-width="2"/>`;
      },
      table(k) {
        const cup = (x, y) => `<circle cx="${x}" cy="${y}" r="20" fill="#EFE6D2" stroke="#C9B48A"/><circle cx="${x}" cy="${y}" r="13" fill="#F7F1E3" stroke="#2F4F7F" stroke-width="2"/><circle cx="${x}" cy="${y}" r="9.5" fill="#8A5A22"/><circle cx="${x - 3}" cy="${y - 3}" r="2.5" fill="#fff" fill-opacity=".4"/>`;
        const tx = 976, ty = 176;
        const pot = `<path d="M${tx + 26} ${ty - 6}L${tx + 54} ${ty - 22}" stroke="#6E3B26" stroke-width="9" stroke-linecap="round"/><path d="M${tx - 30} ${ty - 12}C${tx - 54} ${ty - 12} ${tx - 54} ${ty + 14} ${tx - 30} ${ty + 12}" fill="none" stroke="#6E3B26" stroke-width="6"/><circle cx="${tx}" cy="${ty}" r="32" fill="#6E3B26" stroke="#3B1F14" stroke-width="2"/><circle cx="${tx}" cy="${ty}" r="14" fill="#7D4630" stroke="#3B1F14"/><circle cx="${tx}" cy="${ty}" r="4" fill="#3B1F14"/>`;
        return `<defs><radialGradient id="jd${k}" cx="50%" cy="45%" r="65%"><stop offset="0" stop-color="#3E7B67"/><stop offset="1" stop-color="#234A3D"/></radialGradient></defs>
          <rect x="322" y="136" width="556" height="472" rx="14" fill="#000" opacity=".6" filter="url(#bl${k})"/>
          <rect x="330" y="128" width="540" height="464" rx="12" fill="#3B1F14" stroke="#1E0F09" stroke-width="3"/>
          <rect x="342" y="140" width="516" height="440" rx="6" fill="none" stroke="#6E3A24" stroke-width="2"/>
          <rect x="356" y="154" width="488" height="412" rx="4" fill="url(#jd${k})" stroke="#1E0F09" stroke-width="2"/>
          ${cup(394, 192)}${cup(806, 192)}${cup(394, 528)}${cup(806, 528)}${pot}`;
      },
      plate(x, y, name, n, active, wind) {
        let s = '';
        if (active) s += `<rect x="${x - 34}" y="${y - 34}" width="68" height="68" rx="8" fill="none" stroke="#E7C27D" stroke-width="2"/>`;
        s += `<rect x="${x - 27}" y="${y - 27}" width="54" height="54" rx="5" fill="#B8322A"/><rect x="${x - 23}" y="${y - 23}" width="46" height="46" rx="3" fill="none" stroke="#F3EAD7" stroke-opacity=".6"/>`;
        s += T(x, y + 11, wind, { f: F.noto, s: 30, w: 900, c: '#F3EAD7' });
        s += T(x + 44, y - 1, name, { a: 'start', f: F.corm, s: 22, w: 700, c: active ? '#E7C27D' : '#EFE6D2' });
        if (n != null) s += T(x + 44, y + 20, `${n} cards`, { a: 'start', f: F.corm, s: 16, w: 700, i: 1, c: '#C9B48A' });
        return s;
      },
      button(x, y, label, primary) {
        return `<rect x="${x - 75}" y="${y - 22}" width="150" height="44" rx="4" fill="${primary ? '#B8322A' : '#1E1B18'}" stroke="${primary ? '#7A1E18' : '#C9B48A'}" stroke-width="1.5"/>` + T(x, y + 7, label, { f: F.corm, s: 22, w: 700, c: '#F3EAD7' });
      },
      tableText(x, y, t) { return T(x, y, t, { f: F.corm, s: 26, w: 700, i: 1, c: '#F3EAD7' }); },
      status(x, y, t) { return `<rect x="${x - 185}" y="${y - 19}" width="370" height="38" rx="3" fill="#F3EAD7" stroke="#C9B48A"/><rect x="${x - 173}" y="${y - 8}" width="16" height="16" rx="2" fill="#B8322A"/>` + T(x + 8, y + 6, t, { f: F.corm, s: 19, w: 700, c: '#1E1B18' }); }
    },

    studio: {
      name: 'Drafting Studio', deck: 'blueprint', accent: '#FFD166', edge: '#111418',
      blurb: 'Architect’s studio. A drafting board with blueprint grid and a ruler.',
      floor(k) {
        return `<defs><pattern id="cn${k}" width="40" height="40" patternUnits="userSpaceOnUse"><rect width="40" height="40" fill="#1A1E23"/><circle cx="7" cy="9" r="1" fill="#fff" fill-opacity=".04"/><circle cx="29" cy="23" r="1.4" fill="#fff" fill-opacity=".03"/><circle cx="18" cy="34" r=".8" fill="#000" fill-opacity=".25"/></pattern></defs><rect width="1200" height="760" fill="url(#cn${k})"/>` + vignette(k, .6);
      },
      table(k) {
        let ticks = '';
        for (let i = 0; i <= 72; i++) {
          const x = 240 + i * 10, h = i % 10 === 0 ? 12 : i % 5 === 0 ? 8 : 5;
          ticks += `<line x1="${x}" y1="126" x2="${x}" y2="${126 + h}"/>`;
          if (i % 10 === 0) ticks += T(x + 3, 145, i / 10, { a: 'start', f: F.mono, s: 9, c: '#4A4535' });
        }
        const pencil = `<g transform="translate(905 522) rotate(-28)"><rect x="-70" y="-6" width="120" height="12" fill="#F2C14E" stroke="#8A6A1E"/><line x1="-70" y1="0" x2="50" y2="0" stroke="#8A6A1E" stroke-opacity=".5"/><rect x="50" y="-6" width="12" height="12" fill="#B8BEC6"/><rect x="62" y="-6" width="12" height="12" rx="2" fill="#E88A8A"/><polygon points="-70,-6 -88,0 -70,6" fill="#E8D3A9"/><polygon points="-82,-2 -88,0 -82,2" fill="#3A3A3A"/></g>`;
        return `<defs><pattern id="gm${k}" width="20" height="20" patternUnits="userSpaceOnUse"><path d="M20 0H0V20" fill="none" stroke="#8DB8E0" stroke-opacity=".1"/></pattern><pattern id="gM${k}" width="100" height="100" patternUnits="userSpaceOnUse" x="4" y="14"><path d="M100 0H0V100" fill="none" stroke="#8DB8E0" stroke-opacity=".22"/></pattern></defs>
          <rect x="184" y="158" width="832" height="420" rx="8" fill="#000" opacity=".6" filter="url(#bl${k})"/>
          <rect x="190" y="150" width="820" height="420" rx="6" fill="#AEB6BF" stroke="#6F7883" stroke-width="2"/>
          <rect x="204" y="164" width="792" height="392" fill="#12355E"/>
          <rect x="204" y="164" width="792" height="392" fill="url(#gm${k})"/><rect x="204" y="164" width="792" height="392" fill="url(#gM${k})"/>
          <circle cx="600" cy="360" r="150" fill="none" stroke="#8DB8E0" stroke-opacity=".25" stroke-dasharray="6 6"/>
          <line x1="420" y1="360" x2="500" y2="360" stroke="#8DB8E0" stroke-opacity=".3"/><line x1="700" y1="360" x2="780" y2="360" stroke="#8DB8E0" stroke-opacity=".3"/>
          <rect x="240" y="126" width="720" height="22" fill="#E8E2C9" stroke="#9C9476"/>
          <g stroke="#4A4535">${ticks}</g>
          ${pencil}`;
      },
      plate(x, y, name, n, active, wind, seatNo) {
        const c = active ? '#FFD166' : '#8DB8E0';
        let s = `<rect x="${x - 78}" y="${y - 23}" width="156" height="46" fill="#0F2A4A" stroke="${c}" stroke-width="${active ? 1.8 : 1.2}"/>`;
        s += `<path d="M${x - 84} ${y - 15}V${y - 29}H${x - 70}M${x + 84} ${y + 15}V${y + 29}H${x + 70}" fill="none" stroke="${c}" stroke-width="1.5"/>`;
        s += T(x - 66, y + 6, `P${seatNo} ${name.toUpperCase()}`.slice(0, 12), { a: 'start', f: F.mono, s: 15, w: 700, c: '#DCEBFA', ls: 1 });
        if (n != null) s += T(x + 66, y + 6, String(n).padStart(2, '0'), { a: 'end', f: F.mono, s: 15, w: 500, c });
        if (active) s += T(x, y + 44, 'TO PLAY', { f: F.mono, s: 11, w: 700, ls: 3, c: '#FFD166' });
        return s;
      },
      button(x, y, label, primary) {
        return `<rect x="${x - 75}" y="${y - 22}" width="150" height="44" rx="3" fill="${primary ? '#FFD166' : '#0F2A4A'}" stroke="${primary ? '#C9A243' : '#8DB8E0'}" stroke-width="1.2"/>` + T(x, y + 5, label.toUpperCase(), { f: F.mono, s: 15, w: 800, ls: 3, c: primary ? '#0F2A4A' : '#DCEBFA' });
      },
      tableText(x, y, t) { return T(x, y, t.toUpperCase(), { f: F.mono, s: 15, w: 700, ls: 3, c: '#DCEBFA' }); },
      status(x, y, t) { return `<rect x="${x - 200}" y="${y - 18}" width="400" height="36" fill="#0F2A4A" fill-opacity=".92" stroke="#8DB8E0"/>` + T(x, y + 5, t.toUpperCase(), { f: F.mono, s: 12, w: 500, ls: 1.5, c: '#DCEBFA' }); }
    }
  };

  const DEFAULT_POS = { top: [600, 74], left: [100, 320], right: [1100, 320], me: [110, 668], backsTop: [790, 74], backsLeft: [100, 440], backsRight: [1100, 440] };
  const PLATE_KEYS = ['me', 'left', 'top', 'right'];
  const BACK_KEYS = [null, 'backsLeft', 'backsTop', 'backsRight'];

  function positions(rm) {
    return Object.assign({}, DEFAULT_POS, rm.pos || {});
  }

  function defs(k) {
    return `<defs><filter id="ds${k}" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="3" stdDeviation="3" flood-color="#000" flood-opacity=".5"/></filter><filter id="bl${k}" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation="14"/></filter></defs>`;
  }

  /**
   * view = {
   *   deck, players: [4, by position: 0 you, 1 left, 2 top, 3 right] of { seat, name, count, active },
   *   tableCards: ['7H', ...], label, status, hand: [{ code, selected }], canPlay, canPass
   * }
   */
  function scene(roomId, view) {
    const rm = ROOMS[roomId] || ROOMS['gold-room'];
    const k = uid++, ds = 'ds' + k, p = positions(rm), deck = view.deck;
    const winds = ['東', '南', '西', '北'];
    let o = `<svg class="scene" viewBox="0 0 1200 760" preserveAspectRatio="xMidYMid meet" role="img" aria-label="${esc(rm.name)} table" xmlns="http://www.w3.org/2000/svg">`;
    o += defs(k) + rm.floor(k) + rm.table(k);

    if (view.label) o += rm.tableText(600, 274, view.label);
    const n = view.tableCards.length, tilts = [-5, 6, -3, 4, -2];
    view.tableCards.forEach((code, i) => {
      o += place(Cards.card(deck, code), 600 + (i - (n - 1) / 2) * 58, 352 + (i % 2) * 4, 80, tilts[i % 5], ds);
    });
    if (view.status) o += rm.status(600, 474, view.status);

    view.players.forEach((pl, rel) => {
      if (rel > 0 && pl.count > 0) {
        const [bx, by] = p[BACK_KEYS[rel]];
        const shown = Math.min(pl.count, 4);
        for (let i = 0; i < shown; i++) {
          const off = i - (shown - 1) / 2;
          o += place(Cards.back(deck), bx + off * 13, by, 40, off * 9, ds);
        }
      }
      const [x, y] = p[PLATE_KEYS[rel]];
      o += rm.plate(x, y, pl.name, rel === 0 ? null : pl.count, pl.active, winds[pl.seat], pl.seat + 1);
    });

    const h = view.hand.length;
    view.hand.forEach((c, i) => {
      const d = i - (h - 1) / 2;
      const x = 600 + d * 44, y = 652 + d * d * 0.7;
      o += `<g class="hc" data-card="${c.code}" transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${(d * 2.2).toFixed(2)})" filter="url(#${ds})">` +
        `<g class="lift${c.selected ? ' sel' : ''}">` +
        (c.selected ? `<rect x="-44" y="-59.6" width="88" height="119.2" rx="11" fill="none" stroke="${rm.accent}" stroke-width="4"/>` : '') +
        nest(Cards.card(deck, c.code), 78) + '</g></g>';
    });

    o += `<g class="sbtn${view.canPlay ? '' : ' off'}" data-action="play">${rm.button(1075, 640, 'Play', true)}</g>`;
    o += `<g class="sbtn${view.canPass ? '' : ' off'}" data-action="pass">${rm.button(1075, 700, 'Pass', false)}</g>`;
    o += '<g id="timer-layer"></g>';
    return o + '</svg>';
  }

  /** A slim countdown bar above a seat's plate, drawn into #timer-layer. */
  function timerBar(roomId, rel, fraction, seconds) {
    const rm = ROOMS[roomId] || ROOMS['gold-room'];
    const [x, y] = positions(rm)[PLATE_KEYS[rel]];
    const left = rm.pos && roomId === 'tea-house' ? x - 34 : x - 75;
    const w = 150, top = y - 60;
    const urgent = seconds <= 5;
    const color = urgent ? '#FF6B6B' : rm.accent;
    return `<rect x="${left}" y="${top}" width="${w}" height="5" rx="2.5" fill="#000" fill-opacity=".55"/>` +
      `<rect x="${left}" y="${top}" width="${(w * Math.max(0, Math.min(1, fraction))).toFixed(1)}" height="5" rx="2.5" fill="${color}"/>` +
      `<text x="${left + w + 8}" y="${top + 6}" font-family="${F.mono}" font-size="13" font-weight="700" fill="${color}">${seconds}s</text>`;
  }

  /** Small picture of an empty table for the lobby. */
  function preview(roomId, deckId) {
    const rm = ROOMS[roomId];
    const k = uid++;
    const deck = deckId || rm.deck;
    return `<svg class="room-preview" viewBox="140 90 920 540" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">${defs(k)}${rm.floor(k)}${rm.table(k)}` +
      place(Cards.card(deck, 'AS'), 560, 360, 90, -6) + place(Cards.card(deck, 'KH'), 640, 362, 90, 6) + '</svg>';
  }

  window.Rooms = { ROOMS, scene, timerBar, preview };
})();
