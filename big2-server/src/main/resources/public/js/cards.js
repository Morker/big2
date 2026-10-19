// Card faces and backs for the five deck styles, drawn as SVG in a 200 x 280 box.
// card(deckId, 'QH') returns an <svg> string; back(deckId) returns the deck's back.
(function () {
  const RANKS = ['3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A', '2'];
  const SUITS = ['D', 'C', 'H', 'S'];
  const SUIT_NAME = { D: 'Diamonds', C: 'Clubs', H: 'Hearts', S: 'Spades' };
  const RANK_NAME = { J: 'Jack', Q: 'Queen', K: 'King', A: 'Ace' };
  const ZH = { D: '方塊', C: '梅花', H: '紅心', S: '黑桃' };
  const isRed = s => s === 'D' || s === 'H';
  const value = (r, s) => RANKS.indexOf(r) * 4 + SUITS.indexOf(s);
  let uid = 0;

  function shape(s) {
    switch (s) {
      case 'D': return '<path d="M0 -.55L.42 0L0 .55L-.42 0Z"/>';
      case 'H': return '<path d="M0 .5C-.95 -.05 -.55 -.62 0 -.2C.55 -.62 .95 -.05 0 .5Z"/>';
      case 'S': return '<path d="M0 -.5C-.95 .05 -.55 .62 0 .2C.55 .62 .95 .05 0 -.5Z"/><path d="M0 .1L-.22 .6L.22 .6Z"/>';
      default: return '<circle cy="-.3" r=".27"/><circle cx="-.3" cy=".08" r=".27"/><circle cx=".3" cy=".08" r=".27"/><path d="M0 0L-.22 .6L.22 .6Z"/>';
    }
  }
  function pip(st, s, x, y, size, flip, color) {
    const c = color || st.color(s);
    const tr = `translate(${x} ${y}) scale(${size})${flip ? ' rotate(180)' : ''}`;
    if (st.pip === 'outline') {
      const w = (st.pipStroke || 2) / size;
      return `<g transform="${tr}"><g fill="${c}" stroke="${c}" stroke-width="${2 * w}" stroke-linejoin="round">${shape(s)}</g><g fill="${st.bg}">${shape(s)}</g></g>`;
    }
    return `<g transform="${tr}" fill="${c}">${shape(s)}</g>`;
  }
  const L = 64, M = 100, R = 136;
  const SIX = [[L, .15], [R, .15], [L, .5], [R, .5], [L, .85], [R, .85]];
  const PIPS = {
    '2': [[M, .15], [M, .85]], '3': [[M, .15], [M, .5], [M, .85]], '4': [[L, .15], [R, .15], [L, .85], [R, .85]],
    '5': [[L, .15], [R, .15], [M, .5], [L, .85], [R, .85]], '6': SIX, '7': [...SIX, [M, .32]], '8': [...SIX, [M, .32], [M, .68]],
    '9': [[L, .15], [R, .15], [L, .38], [R, .38], [M, .5], [L, .62], [R, .62], [L, .85], [R, .85]],
    '10': [[L, .15], [R, .15], [L, .38], [R, .38], [M, .265], [M, .735], [L, .62], [R, .62], [L, .85], [R, .85]]
  };
  const py = t => Math.round(64 + t * 152);
  function index(st, r, s) {
    const c = st.color(s), ten = r === '10', y = st.iy || 46;
    const one = `<text x="28" y="${y}" text-anchor="middle" font-family="${st.font}" font-weight="${st.fw}" font-size="${ten ? st.fs * .8 : st.fs}" fill="${c}"${ten ? ' letter-spacing="-2"' : ''}>${r}</text>${pip(st, s, 28, y + 24, 20, false)}`;
    return one + `<g transform="rotate(180 100 140)">${one}</g>`;
  }
  function frame(st) {
    return `<rect x="1.5" y="1.5" width="197" height="277" rx="${st.radius}" fill="${st.bg}" stroke="${st.border}" stroke-width="${st.borderW || 1.5}"/>`;
  }
  function svg(inner, label) {
    return `<svg class="card" viewBox="0 0 200 280" role="img" aria-label="${label}" xmlns="http://www.w3.org/2000/svg">${inner}</svg>`;
  }

  const DECKS = {
    noir: {
      name: 'Noir & Gold', bg: '#111215', border: '#C9A24B', radius: 12, font: "'Bodoni Moda', Didot, Georgia, serif", fw: 700, fs: 40,
      color: s => isRed(s) ? '#E04B55' : '#ECE6D6', pip: 'fill',
      deco() { return '<rect x="9" y="9" width="182" height="262" rx="7" fill="none" stroke="#C9A24B" stroke-opacity=".45"/>'; },
      court(r, s) { const g = '#C9A24B'; return `<line x1="62" y1="104" x2="138" y2="104" stroke="${g}" stroke-opacity=".6"/><line x1="62" y1="198" x2="138" y2="198" stroke="${g}" stroke-opacity=".6"/>${pip(this, s, 100, 78, 26)}<text x="100" y="184" text-anchor="middle" font-family="${this.font}" font-weight="700" font-size="96" fill="${g}">${r}</text>${pip(this, s, 100, 222, 22, true)}`; },
      ace(s) { return `<rect x="62" y="102" width="76" height="76" transform="rotate(45 100 140)" fill="none" stroke="#C9A24B" stroke-opacity=".75"/><rect x="69" y="109" width="62" height="62" transform="rotate(45 100 140)" fill="none" stroke="#C9A24B" stroke-opacity=".3"/>${pip(this, s, 100, 140, 62)}`; },
      back() { const id = 'n' + (uid++); return svg(`<defs><pattern id="${id}" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><path d="M0 0V8" stroke="#C9A24B" stroke-opacity=".16"/></pattern></defs>${frame(this)}<rect x="9" y="9" width="182" height="262" rx="7" fill="url(#${id})" stroke="#C9A24B" stroke-opacity=".7"/><rect x="16" y="16" width="168" height="248" rx="4" fill="none" stroke="#C9A24B" stroke-opacity=".35"/><rect x="72" y="112" width="56" height="56" transform="rotate(45 100 140)" fill="#111215" stroke="#C9A24B"/><text x="100" y="149" text-anchor="middle" font-family="${this.font}" font-weight="700" font-size="26" fill="#C9A24B">B2</text>`, 'Card back'); }
    },
    classic: {
      name: 'Classic Parlour', bg: '#FBF8F1', border: '#2B2B2B', radius: 12, font: "'Cormorant Garamond', Garamond, Georgia, serif", fw: 700, fs: 42,
      color: s => isRed(s) ? '#B3202A' : '#16181D', pip: 'fill',
      court(r, s) { const c = this.color(s); return `<rect x="50" y="46" width="100" height="188" rx="6" fill="none" stroke="${c}" stroke-width="1.6"/><rect x="56" y="52" width="88" height="176" rx="3" fill="none" stroke="${c}" stroke-opacity=".35"/>${pip(this, s, 100, 82, 28)}<text x="100" y="186" text-anchor="middle" font-family="${this.font}" font-weight="700" font-size="104" fill="${c}">${r}</text>${pip(this, s, 100, 207, 22, true)}`; },
      ace(s) { const c = this.color(s); return `<circle cx="100" cy="140" r="60" fill="none" stroke="${c}" stroke-width="1.4"/><circle cx="100" cy="140" r="53" fill="none" stroke="${c}" stroke-opacity=".3"/>${pip(this, s, 100, 140, 78)}`; },
      back() { const id = 'c' + (uid++); return svg(`<defs><pattern id="${id}" width="12" height="12" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><path d="M0 0H12M0 0V12" stroke="#F3D9C9" stroke-opacity=".45" stroke-width="1.2" fill="none"/></pattern></defs>${frame(this)}<rect x="10" y="10" width="180" height="260" rx="7" fill="#7A1C22"/><rect x="10" y="10" width="180" height="260" rx="7" fill="url(#${id})"/><rect x="17" y="17" width="166" height="246" rx="4" fill="none" stroke="#F3D9C9" stroke-opacity=".7"/><ellipse cx="100" cy="140" rx="36" ry="48" fill="#7A1C22" stroke="#F3D9C9" stroke-opacity=".8"/><g transform="translate(100 138) scale(42)" fill="#F3D9C9">${shape('S')}</g>`, 'Card back'); }
    },
    deco: {
      name: 'Riviera Deco', bg: '#F2EBDB', border: '#1C2A44', borderW: 2, radius: 10, font: "'Limelight', Georgia, serif", fw: 400, fs: 36,
      color: s => isRed(s) ? '#A3302B' : '#1C2A44', pip: 'fill',
      deco() { const g = '#B8913A'; return `<rect x="9" y="9" width="182" height="262" rx="4" fill="none" stroke="${g}" stroke-width="1.5"/><rect x="13" y="13" width="174" height="254" rx="2" fill="none" stroke="${g}" stroke-opacity=".5"/><path d="M82 13L100 25L118 13M82 267L100 255L118 267" fill="none" stroke="${g}" stroke-width="1.5"/>`; },
      court(r, s) { const id = 'k' + (uid++), g = '#B8913A'; let rays = ''; for (let i = -6; i <= 6; i++) { const a = i * 12 * Math.PI / 180; rays += `<line x1="100" y1="232" x2="${(100 + Math.sin(a) * 190).toFixed(1)}" y2="${(232 - Math.cos(a) * 190).toFixed(1)}"/>`; } return `<defs><clipPath id="${id}"><rect x="50" y="48" width="100" height="184"/></clipPath></defs><g clip-path="url(#${id})" stroke="${g}" stroke-opacity=".45" stroke-width="1.2">${rays}</g><rect x="50" y="48" width="100" height="184" fill="none" stroke="${g}" stroke-width="1.5"/>${pip(this, s, 100, 82, 26)}<text x="100" y="198" text-anchor="middle" font-family="${this.font}" font-size="92" fill="${this.color(s)}">${r}</text>`; },
      ace(s) { const g = '#B8913A'; let rays = ''; for (let i = 0; i < 24; i++) { const a = i * 15 * Math.PI / 180; rays += `<line x1="${(100 + Math.cos(a) * 46).toFixed(1)}" y1="${(140 + Math.sin(a) * 46).toFixed(1)}" x2="${(100 + Math.cos(a) * 60).toFixed(1)}" y2="${(140 + Math.sin(a) * 60).toFixed(1)}"/>`; } return `<g stroke="${g}" stroke-width="1.5">${rays}</g><circle cx="100" cy="140" r="42" fill="none" stroke="${g}" stroke-width="1.5"/>${pip(this, s, 100, 140, 54)}`; },
      back() { const id = 'd' + (uid++), g = '#B8913A'; let rays = ''; for (let i = -8; i <= 8; i++) { const a = i * 10 * Math.PI / 180; rays += `<line x1="100" y1="250" x2="${(100 + Math.sin(a) * 260).toFixed(1)}" y2="${(250 - Math.cos(a) * 260).toFixed(1)}"/>`; } return svg(`<defs><clipPath id="${id}"><rect x="13" y="13" width="174" height="254" rx="2"/></clipPath></defs><rect x="1.5" y="1.5" width="197" height="277" rx="10" fill="#1C2A44" stroke="#1C2A44" stroke-width="2"/><g clip-path="url(#${id})" stroke="${g}" stroke-opacity=".55">${rays}</g><rect x="9" y="9" width="182" height="262" rx="4" fill="none" stroke="${g}" stroke-width="1.5"/><rect x="13" y="13" width="174" height="254" rx="2" fill="none" stroke="${g}" stroke-opacity=".5"/><path d="M60 250A40 40 0 0 1 140 250Z" fill="#1C2A44" stroke="${g}" stroke-width="1.5"/><text x="100" y="244" text-anchor="middle" font-family="${this.font}" font-size="22" fill="${g}">B2</text>`, 'Card back'); }
    },
    teahouse: {
      name: 'Tea House', bg: '#F3EAD7', border: '#3A2A1D', radius: 12, font: "'Noto Serif TC', 'Songti TC', 'PMingLiU', serif", fw: 900, fs: 38,
      color: s => isRed(s) ? '#B8322A' : '#1E1B18', pip: 'fill',
      court(r, s) { const seal = { J: '鉤', Q: '女', K: '老' }[r]; return `${pip(this, s, 100, 72, 26)}<rect x="60" y="96" width="80" height="80" rx="6" fill="#B8322A"/><rect x="65" y="101" width="70" height="70" rx="3" fill="none" stroke="#F3EAD7" stroke-opacity=".7"/><text x="100" y="156" text-anchor="middle" font-family="${this.font}" font-weight="900" font-size="52" fill="#F3EAD7">${seal}</text><text x="104" y="206" text-anchor="middle" font-family="${this.font}" font-weight="900" font-size="18" letter-spacing="8" fill="${this.color(s)}">${ZH[s]}</text>${pip(this, s, 100, 230, 18, true)}`; },
      ace(s) { return `<circle cx="100" cy="140" r="58" fill="none" stroke="#1E1B18" stroke-opacity=".55" stroke-width="5" stroke-linecap="round" stroke-dasharray="300 64" transform="rotate(-60 100 140)"/>${pip(this, s, 100, 140, 68)}`; },
      extra(r) { return r !== '2' ? '' : `<rect x="80" y="120" width="40" height="40" rx="4" fill="#B8322A"/><text x="100" y="150" text-anchor="middle" font-family="${this.font}" font-weight="900" font-size="28" fill="#F3EAD7">大</text>`; },
      back() { const id = 't' + (uid++); return svg(`<defs><pattern id="${id}" width="16" height="16" patternUnits="userSpaceOnUse"><path d="M2 2H14V14H6V6H10V10" fill="none" stroke="#E7C27D" stroke-opacity=".35" stroke-width="1.2"/></pattern></defs>${frame(this)}<rect x="10" y="10" width="180" height="260" rx="7" fill="#9E2B25"/><rect x="10" y="10" width="180" height="260" rx="7" fill="url(#${id})"/><rect x="17" y="17" width="166" height="246" rx="4" fill="none" stroke="#E7C27D" stroke-opacity=".7"/><circle cx="100" cy="140" r="44" fill="#F3EAD7" stroke="#E7C27D" stroke-width="2"/><text x="100" y="148" text-anchor="middle" font-family="${this.font}" font-weight="900" font-size="22" fill="#9E2B25">鋤大D</text>`, 'Card back'); }
    },
    blueprint: {
      name: 'Blueprint', bg: '#0F2A4A', border: '#8DB8E0', borderW: 1.2, radius: 8, font: "'JetBrains Mono', ui-monospace, Consolas, monospace", fw: 700, fs: 32, iy: 44,
      color: s => isRed(s) ? '#FF8F86' : '#DCEBFA', pip: 'outline', pipStroke: 2.2,
      deco() { const id = 'g' + (uid++); return `<defs><pattern id="${id}" width="20" height="20" patternUnits="userSpaceOnUse"><path d="M20 0H0V20" fill="none" stroke="#8DB8E0" stroke-opacity=".13"/></pattern></defs><rect x="2" y="2" width="196" height="276" rx="7" fill="url(#${id})"/>`; },
      court(r, s) { return `<line x1="40" y1="140" x2="160" y2="140" stroke="#8DB8E0" stroke-opacity=".35" stroke-dasharray="4 4"/><line x1="100" y1="60" x2="100" y2="222" stroke="#8DB8E0" stroke-opacity=".35" stroke-dasharray="4 4"/>${pip(this, s, 100, 78, 26)}<text x="100" y="186" text-anchor="middle" font-family="${this.font}" font-weight="800" font-size="104" fill="none" stroke="${this.color(s)}" stroke-width="2">${r}</text>`; },
      ace(s) { return `<circle cx="100" cy="140" r="58" fill="none" stroke="#8DB8E0" stroke-opacity=".55" stroke-dasharray="3 5"/><line x1="30" y1="140" x2="170" y2="140" stroke="#8DB8E0" stroke-opacity=".3"/><line x1="100" y1="70" x2="100" y2="210" stroke="#8DB8E0" stroke-opacity=".3"/>${pip(this, s, 100, 140, 70)}`; },
      extra(r, s) { return `<text x="100" y="262" text-anchor="middle" font-family="${this.font}" font-size="12" font-weight="500" fill="#8DB8E0" letter-spacing="1.5">VAL ${String(value(r, s)).padStart(2, '0')}</text>`; },
      back() { return svg(frame(this) + this.deco() + `<rect x="10" y="10" width="180" height="260" rx="4" fill="none" stroke="#8DB8E0" stroke-opacity=".6"/><circle cx="100" cy="140" r="48" fill="#0F2A4A" stroke="#8DB8E0"/><circle cx="100" cy="140" r="36" fill="none" stroke="#8DB8E0" stroke-opacity=".5" stroke-dasharray="3 4"/><line x1="40" y1="140" x2="160" y2="140" stroke="#8DB8E0" stroke-opacity=".5"/><line x1="100" y1="80" x2="100" y2="200" stroke="#8DB8E0" stroke-opacity=".5"/><text x="100" y="46" text-anchor="middle" font-family="${this.font}" font-weight="700" font-size="16" fill="#DCEBFA" letter-spacing="3">BIG 2</text><text x="100" y="246" text-anchor="middle" font-family="${this.font}" font-size="11" fill="#8DB8E0" letter-spacing="2">52 CARDS</text>`, 'Card back'); }
    }
  };

  /** Splits a code like '10S' into ['10', 'S']. */
  function parse(code) {
    return [code.slice(0, -1).toUpperCase(), code.slice(-1).toUpperCase()];
  }

  function card(deckId, code) {
    const st = DECKS[deckId] || DECKS.noir;
    const [r, s] = parse(code);
    let body;
    if (st.body) body = st.body(r, s);
    else if (PIPS[r]) body = PIPS[r].map(([x, t]) => pip(st, s, x, py(t), 32, t > .5)).join('');
    else if (r === 'A') body = st.ace ? st.ace(s) : pip(st, s, 100, 140, 96);
    else body = st.court(r, s);
    return svg(frame(st) + (st.deco ? st.deco(r, s) : '') + body + (st.extra ? st.extra(r, s) : '') + index(st, r, s),
      `${RANK_NAME[r] || r} of ${SUIT_NAME[s]}`);
  }

  function back(deckId) {
    return (DECKS[deckId] || DECKS.noir).back();
  }

  /** Big 2 strength 0 (3♦) to 51 (2♠); used for sorting. */
  function strength(code) {
    const [r, s] = parse(code);
    return value(r, s);
  }

  /** Display form like '10♠'. */
  function pretty(code) {
    const [r, s] = parse(code);
    return r + { D: '♦', C: '♣', H: '♥', S: '♠' }[s];
  }

  window.Cards = { DECKS, RANKS, SUITS, card, back, strength, pretty, parse };
})();
