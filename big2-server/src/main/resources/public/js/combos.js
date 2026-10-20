// Big 2 hand rules in the browser, mirroring the server's HandEvaluator (house rules included).
// Used for the quick-select chips and to explain an illegal play before it is sent.
// The server still checks every move; this only makes the game friendlier.
(function () {
  const SUITS = ['D', 'C', 'H', 'S'];
  const TYPES = ['single', 'pair', 'triple', 'straight', 'flush', 'fullhouse', 'quads', 'straightflush'];
  const LABELS = {
    single: 'Single', pair: 'Pair', triple: 'Triple', straight: 'Straight', flush: 'Flush',
    fullhouse: 'Full house', quads: '4 of a kind', straightflush: 'Str. flush'
  };
  const SIZE = { single: 1, pair: 2, triple: 3, straight: 5, flush: 5, fullhouse: 5, quads: 5, straightflush: 5 };

  function info(code) {
    const [r, s] = Cards.parse(code);
    const rank = Cards.RANKS.indexOf(r);
    return { code, r, s, rank, value: rank * 4 + SUITS.indexOf(s) };
  }

  const PLURAL = { '3': '3s', '4': '4s', '5': '5s', '6': '6s', '7': '7s', '8': '8s', '9': '9s', '10': '10s', J: 'Js', Q: 'Qs', K: 'Ks', A: 'aces', '2': '2s' };

  /** Value of the card that ranks a straight, or -1 if these 5 sorted cards aren't one. */
  function straightKey(sorted, rules) {
    let consecutive = true;
    for (let i = 1; i < 5; i++) {
      if (sorted[i].rank !== sorted[i - 1].rank + 1) { consecutive = false; break; }
    }
    const hasTwo = sorted.some(c => c.r === '2');
    if (consecutive) return hasTwo && !rules.twos ? -1 : sorted[4].value;
    if (rules.wrap) {
      const set = sorted.map(c => c.r).sort().join(',');
      const of = r => sorted.find(c => c.r === r).value;
      if (set === ['A', '2', '3', '4', '5'].sort().join(',')) return of('5');
      if (set === ['2', '3', '4', '5', '6'].sort().join(',')) return of('6');
    }
    return -1;
  }

  /** { type, size, key, cards } for a legal combination, or null. */
  function evaluate(codes, rules) {
    const cards = codes.map(info).sort((a, b) => a.value - b.value);
    const n = cards.length;
    if (new Set(codes).size !== n) return null;
    const top = cards[n - 1];
    const sameRank = cards.every(c => c.rank === cards[0].rank);
    if (n === 1) return { type: 'single', size: 1, key: top.value, cards };
    if (n === 2) return sameRank ? { type: 'pair', size: 2, key: top.value, cards } : null;
    if (n === 3) return sameRank ? { type: 'triple', size: 3, key: top.value, cards } : null;
    if (n !== 5) return null;

    const flush = cards.every(c => c.s === cards[0].s);
    const sKey = straightKey(cards, rules);
    if (sKey >= 0 && flush) return { type: 'straightflush', size: 5, key: sKey, cards };
    const groups = {};
    cards.forEach(c => { (groups[c.rank] = groups[c.rank] || []).push(c); });
    const sizes = Object.values(groups);
    const four = sizes.find(g => g.length === 4);
    if (four) return { type: 'quads', size: 5, key: four[3].value, cards };
    const three = sizes.find(g => g.length === 3);
    if (three && sizes.length === 2) return { type: 'fullhouse', size: 5, key: three[2].value, cards };
    if (flush) return { type: 'flush', size: 5, key: top.value, cards };
    if (sKey >= 0) return { type: 'straight', size: 5, key: sKey, cards };
    return null;
  }

  function beats(a, b) {
    if (!b) return true;
    if (a.size !== b.size) return false;
    if (a.size === 5 && a.type !== b.type) return TYPES.indexOf(a.type) > TYPES.indexOf(b.type);
    return a.key > b.key;
  }

  /** Every legal combination in the hand, weakest first ("smallest cards first"). */
  function all(hand, rules) {
    const out = [];
    const n = hand.length;
    const add = codes => { const h = evaluate(codes, rules); if (h) out.push(h); };
    for (let a = 0; a < n; a++) {
      add([hand[a]]);
      for (let b = a + 1; b < n; b++) {
        add([hand[a], hand[b]]);
        for (let c = b + 1; c < n; c++) {
          add([hand[a], hand[b], hand[c]]);
          for (let d = c + 1; d < n; d++) {
            for (let e = d + 1; e < n; e++) add([hand[a], hand[b], hand[c], hand[d], hand[e]]);
          }
        }
      }
    }
    const sum = h => h.cards.reduce((t, c) => t + c.value, 0);
    return out.sort((x, y) => TYPES.indexOf(x.type) - TYPES.indexOf(y.type) || x.key - y.key || sum(x) - sum(y));
  }

  /**
   * Plays grouped by type, each list weakest first. When following, only plays that beat the
   * table; on the opening play, only plays with 3♦.
   */
  function options(hand, tableCodes, rules, mustOpen) {
    const table = tableCodes && tableCodes.length ? evaluate(tableCodes, rules) : null;
    const byType = {};
    for (const h of all(hand, rules)) {
      if (mustOpen && !h.cards.some(c => c.code === '3D')) continue;
      if (!beats(h, table)) continue;
      (byType[h.type] = byType[h.type] || []).push(h);
    }
    return byType;
  }

  /** Words for a hand, e.g. "a pair of 9s", "K♥", "a straight to 7♠". */
  function describe(h) {
    const top = h.cards.find(c => c.value === h.key) || h.cards[h.cards.length - 1];
    const card = Cards.pretty(top.code);
    switch (h.type) {
      case 'single': return card;
      case 'pair': return `a pair of ${PLURAL[top.r]}`;
      case 'triple': return `three ${PLURAL[top.r]}`;
      case 'straight': return `a straight to ${card}`;
      case 'flush': return `a flush (${card} high)`;
      case 'fullhouse': return `a full house of ${PLURAL[top.r]}`;
      case 'quads': return `four ${PLURAL[top.r]}`;
      default: return `a straight flush to ${card}`;
    }
  }

  /** Why the selected cards can't be played now, or null if they can. */
  function problem(selected, tableCodes, rules, mustOpen) {
    if (!selected.length) return 'Pick the cards you want to play first.';
    const h = evaluate(selected, rules);
    const table = tableCodes && tableCodes.length ? evaluate(tableCodes, rules) : null;
    if (!h) {
      const n = selected.length;
      if (n === 4) return "That's not a valid hand: 4 cards can't be played. Plays are 1, 2, 3 or 5 cards.";
      if (n > 5) return `That's not a valid hand: ${n} cards is too many. The most you can play is 5.`;
      if (n === 2 || n === 3) return `That's not a valid hand: ${n} cards must all be the same rank.`;
      return "That's not a valid hand: 5 cards must make a straight, flush, full house, four of a kind or straight flush.";
    }
    if (mustOpen && !selected.includes('3D')) return 'Your first play must include 3♦.';
    if (table && h.size !== table.size) {
      const what = table.size === 1 ? 'a single card' : describe(table);
      return `The table has ${what}: play ${table.size} card${table.size > 1 ? 's' : ''} or pass.`;
    }
    if (table && !beats(h, table)) {
      const mine = describe(h).replace(/^a /, 'your ');
      return `${mine.charAt(0).toUpperCase() + mine.slice(1)} doesn't beat ${describe(table)}.`;
    }
    return null;
  }

  window.Combos = { TYPES, LABELS, SIZE, evaluate, beats, options, describe, problem };
})();
