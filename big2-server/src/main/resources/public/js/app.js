// Big 2 web client: talks to the server over a WebSocket (one protocol line per message)
// and draws three screens: home, lobby (owner settings) and the table.
(function () {
  const $ = sel => document.querySelector(sel);
  const ROOM_IDS = ['gold-room', 'library', 'salon', 'tea-house', 'studio'];
  const DECK_IDS = ['noir', 'classic', 'deco', 'teahouse', 'blueprint'];
  const BOT_NOTES = {
    easy: 'Plays the weakest card that beats the table, every time. Good for learning.',
    normal: 'Plans its hand, keeps its combinations together, saves its 2s and plays hard when someone is nearly out.',
    hard: "Everything Normal does, plus it counts cards and remembers who passed, to know when its plays can't be beaten."
  };
  const DECK_NOTES = { noir: 'Black and gold', classic: 'Ivory and burgundy', deco: 'Navy and cream', teahouse: 'Ink and vermilion', blueprint: 'Outlined pips' };

  const S = {
    view: 'home',
    room: null, mySeat: 0, owner: -1,
    names: [null, null, null, null], tableNames: [null, null, null, null],
    settings: { room: 'gold-room', deck: 'noir', mode: 'single', points: 50, rounds: 5, timer: 30, twos: false, wrap: false, every3d: true, bots: 'normal' },
    phase: 'lobby', deal: 0,
    hand: [], selected: new Set(), counts: [0, 0, 0, 0],
    turn: -1, leading: true, turnSeconds: 0, turnDeadline: 0,
    table: [], tableOwner: -1, playsThisDeal: 0, lastAction: '', lastPass: '',
    penalties: [0, 0, 0, 0], scores: [0, 0, 0, 0], dealWinner: -1,
    overlay: null, nextAt: 0, matchWinner: -1, lastResult: null, reveal: [[], [], [], []],
    leaveArmed: false,
    key: null, away: new Set(), restoring: false, reconnecting: false, rejoining: false
  };

  /** How long the winning play stays visible before the results appear. */
  const REVEAL_MS = 2600;

  /** Plays a sound effect, except while a reconnect snapshot is being replayed. */
  const sfx = (name, arg) => { if (!S.restoring) Sound.effect(name, arg); };

  // ---------- Animations ----------
  // Each entry remembers when it started; rooms.js turns that into CSS animations that survive re-renders.
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let A = { deal: null, play: null, shift: null, sweep: null, shake: null, passes: [], reactions: [] };
  const relOf = seat => (seat - S.mySeat + 4) % 4;
  const orientation = () => (window.innerHeight > window.innerWidth ? 'portrait' : 'landscape');

  function animate(kind, data) {
    // Reactions are messages from other players, so they show even with reduced motion (as a plain fade).
    if (S.restoring || (reduceMotion && kind !== 'react')) return;
    const entry = Object.assign({ start: performance.now() }, data);
    if (kind === 'pass') A.passes.push(entry);
    else if (kind === 'react') A.reactions.push(entry);
    else A[kind] = entry;
  }

  /** The animations still running, with how far along each one is. */
  function liveAnimations() {
    const now = performance.now(), D = Rooms.DURATIONS, out = {};
    for (const kind of ['deal', 'play', 'shift', 'sweep', 'shake']) {
      const a = A[kind];
      if (a && now - a.start < D[kind]) out[kind] = Object.assign({}, a, { elapsed: now - a.start });
      else A[kind] = null;
    }
    A.passes = A.passes.filter(p => now - p.start < D.pass);
    out.passes = A.passes.map(p => ({ rel: p.rel, elapsed: now - p.start }));
    A.reactions = A.reactions.filter(r => now - r.start < D.react);
    out.reactions = A.reactions.map(r => ({ rel: r.rel, id: r.id, elapsed: now - r.start }));
    return out;
  }

  // ---------- Connection ----------

  let ws = null;
  let queued = [];
  let leaving = false;

  function connect() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;
    const url = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
    ws = new WebSocket(url);
    ws.onopen = () => { queued.forEach(l => ws.send(l)); queued = []; };
    ws.onmessage = e => String(e.data).split('\n').forEach(line => line && handle(line));
    ws.onclose = () => {
      ws = null;
      if (leaving) { leaving = false; return; }
      if (S.room && S.key) {
        scheduleReconnect();
      } else if (S.view === 'home') {
        $('#home-error').textContent = 'Could not reach the game server. Check your connection and try again.';
      }
    };
  }

  // ---------- Reconnecting ----------
  // The server holds a dropped player's seat for a while. Our seat key (TOKEN) gets it back.

  const SEAT_KEY = 'big2-seat';
  const SEAT_MAX_AGE_MS = 6 * 60 * 60 * 1000;
  let reconnectTries = 0, reconnectTimer = 0;

  function saveSeat() {
    if (!S.room || !S.key) return;
    try { localStorage.setItem(SEAT_KEY, JSON.stringify({ room: S.room, key: S.key, at: Date.now() })); } catch (e) { /* storage unavailable */ }
  }
  function loadSeat() {
    try {
      const seat = JSON.parse(localStorage.getItem(SEAT_KEY));
      return seat && seat.room && seat.key && Date.now() - seat.at < SEAT_MAX_AGE_MS ? seat : null;
    } catch (e) { return null; }
  }
  function forgetSeat() {
    try { localStorage.removeItem(SEAT_KEY); } catch (e) { /* storage unavailable */ }
  }

  function rejoin() {
    clearTimeout(reconnectTimer);
    send(`REJOIN ${S.room} ${S.key}`);
  }

  function scheduleReconnect() {
    S.reconnecting = true;
    showReconnecting();
    clearTimeout(reconnectTimer);
    const wait = Math.min(10000, 1000 * Math.pow(2, reconnectTries++));
    reconnectTimer = setTimeout(rejoin, wait);
  }

  function showReconnecting() {
    $('#reconnect').hidden = !S.reconnecting;
  }

  /** The seat could not be taken back (game over, or the seat was given up): start fresh. */
  function giveUpRejoin(message) {
    forgetSeat();
    S.reconnecting = false;
    S.rejoining = false;
    reconnectTries = 0;
    showReconnecting();
    leaving = true;
    if (ws) ws.close();
    Object.assign(S, { view: 'home', room: null, key: null, owner: -1, names: [null, null, null, null], tableNames: [null, null, null, null], hand: [], selected: new Set(), overlay: null, phase: 'lobby', away: new Set() });
    history.replaceState(null, '', location.pathname);
    render();
    if (message) $('#home-error').textContent = message;
  }

  // Phones close connections in the background: try again as soon as the page is visible.
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && S.room && S.key && (!ws || ws.readyState > 1)) {
      reconnectTries = 0;
      rejoin();
    }
  });

  function send(line) {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(line);
    else { queued.push(line); connect(); }
  }

  setInterval(() => { if (ws && ws.readyState === WebSocket.OPEN) ws.send('PING'); }, 25000);

  // ---------- Protocol ----------

  function handle(line) {
    const sp = line.indexOf(' ');
    const cmd = sp < 0 ? line : line.slice(0, sp);
    const rest = sp < 0 ? '' : line.slice(sp + 1);
    const nums = () => rest.trim().split(/\s+/).map(Number);

    switch (cmd) {
      case 'WELCOME':
        return;
      case 'SEAT':
        S.mySeat = Number(rest);
        if (!S.restoring) S.view = 'lobby';
        break;
      case 'TOKEN':
        S.key = rest.trim();
        saveSeat();
        break;
      case 'RESUME':
        S.restoring = true;
        Object.assign(S, { table: [], tableOwner: -1, overlay: null, dealWinner: -1, matchWinner: -1, lastAction: '', lastPass: '', away: new Set(), turnDeadline: 0 });
        S.selected.clear();
        A = { deal: null, play: null, shift: null, sweep: null, shake: null, passes: [], reactions: [] };
        return;
      case 'RESUMED': {
        S.restoring = false;
        S.revealUntil = 0;
        S.view = S.phase === 'lobby' ? 'lobby' : 'table';
        const wasAway = S.reconnecting || S.rejoining;
        S.reconnecting = false;
        S.rejoining = false;
        reconnectTries = 0;
        showReconnecting();
        saveSeat();
        if (wasAway) toast("You're back in the game.");
        break;
      }
      case 'TABLE': {
        const parts = rest.trim().split(' ');
        S.tableOwner = Number(parts[0]);
        S.table = parts.slice(1);
        S.lastAction = `${who(S.tableOwner)} played ${describe(S.table)}`;
        break;
      }
      case 'AWAY': {
        const seat = Number(rest);
        S.away.add(seat);
        if (!S.restoring) toast(`${who(seat)} lost connection. Their seat is kept for a few minutes.`);
        break;
      }
      case 'BACK': {
        const seat = Number(rest);
        const was = S.away.delete(seat);
        if (was && seat !== S.mySeat) toast(`${who(seat)} is back.`);
        break;
      }
      case 'LEFT': {
        const seat = Number(rest);
        S.away.delete(seat);
        toast(S.phase === 'lobby' ? `${who(seat)} left the room.` : `${who(seat)} left. A bot plays their seat now.`);
        break;
      }
      case 'LOBBY': {
        const i = rest.indexOf(' ');
        S.room = rest.slice(0, i);
        saveSeat();
        const seatedBefore = S.names.filter(Boolean).length;
        S.names = rest.slice(i + 1).split(',').map(n => (n === '-' ? null : n));
        if (S.view === 'lobby' && S.names.filter(Boolean).length > seatedBefore && seatedBefore > 0) sfx('join');
        // While the final results are showing, keep the names people played under (bot seats get freed now).
        if (S.overlay !== 'match') S.tableNames = [...S.names];
        if (location.hash.slice(1) !== S.room) history.replaceState(null, '', '#' + S.room);
        break;
      }
      case 'OWNER':
        S.owner = Number(rest);
        break;
      case 'SETTINGS':
        S.settings = parseSettings(rest);
        break;
      case 'PHASE':
        S.phase = rest;
        if (rest === 'playing') { S.view = 'table'; S.overlay = null; S.restarting = false; }
        if (rest === 'lobby' && S.overlay !== 'match') S.view = 'lobby';
        break;
      case 'DEAL':
        S.deal = Number(rest);
        S.table = []; S.tableOwner = -1; S.playsThisDeal = 0;
        S.lastAction = ''; S.lastPass = ''; S.selected.clear(); S.dealWinner = -1; S.revealUntil = 0;
        S.reveal = [[], [], [], []];
        if (S.deal === 1) { S.scores = [0, 0, 0, 0]; S.matchWinner = -1; }
        A = { deal: null, play: null, shift: null, sweep: null, shake: null, passes: [], reactions: [] };
        animate('deal');
        S.quick = null;
        sfx('deal');
        break;
      case 'HAND': {
        const before = S.hand;
        S.hand = rest.trim() ? rest.trim().split(/\s+/).sort((a, b) => Cards.strength(a) - Cards.strength(b)) : [];
        if (S.hand.length < before.length && S.hand.every(c => before.includes(c))) {
          animate('shift', { prevHand: before });
        }
        for (const c of [...S.selected]) if (!S.hand.includes(c)) S.selected.delete(c);
        break;
      }
      case 'COUNTS':
        S.counts = nums();
        break;
      case 'TURN': {
        const [seat, mode, secs] = rest.split(/\s+/);
        const prevOwner = S.tableOwner;
        S.turn = Number(seat);
        S.leading = mode === 'LEAD';
        S.turnSeconds = Number(secs || 0);
        S.turnDeadline = S.turnSeconds > 0 ? Date.now() + S.turnSeconds * 1000 : 0;
        if (S.turn === S.mySeat) sfx('turn');
        if (S.leading && S.table.length) {
          animate('sweep', { cards: [...S.table], rel: relOf(prevOwner) });
          sfx('sweep');
          S.lastAction = `${who(prevOwner)} won the trick`;
          S.table = []; S.tableOwner = -1; S.lastPass = '';
        }
        break;
      }
      case 'PLAYED': {
        const parts = rest.split(/\s+/);
        const seat = Number(parts[0]);
        animate('play', { rel: relOf(seat), prevHand: seat === S.mySeat ? [...S.hand] : null });
        sfx('play', parts.length - 1);
        S.quick = null;
        S.table = parts.slice(1);
        S.tableOwner = seat;
        S.playsThisDeal++;
        S.lastPass = '';
        S.lastAction = `${who(seat)} played ${describe(S.table)}`;
        if (seat === S.mySeat) S.selected.clear();
        break;
      }
      case 'REACT': {
        const [seat, id] = rest.split(/\s+/);
        animate('react', { rel: relOf(Number(seat)), id });
        sfx('pop');
        break;
      }
      case 'PASSED':
        S.lastPass = `${who(Number(rest))} passed`;
        animate('pass', { rel: relOf(Number(rest)) });
        sfx('pass');
        break;
      case 'TIMEOUT': {
        const seat = Number(rest);
        toast(seat === S.mySeat ? 'Your time ran out, so the server moved for you.' : `${who(seat)} ran out of time.`);
        break;
      }
      case 'REVEAL': {
        const [seat, ...cards] = rest.trim().split(/\s+/);
        S.reveal[Number(seat)] = cards.filter(Boolean).sort((a, b) => Cards.strength(a) - Cards.strength(b));
        return; // drawn with the results
      }
      case 'WIN':
        S.revealUntil = Date.now() + REVEAL_MS;
        setTimeout(() => { if (S.view === 'table') renderTable(); }, REVEAL_MS + 30);
        S.dealWinner = Number(rest.split(/\s+/)[0]);
        S.turnDeadline = 0;
        sfx(S.dealWinner === S.mySeat ? 'win' : 'lose');
        break;
      case 'SCORES': {
        const n = nums();
        S.penalties = n.slice(0, 4);
        S.scores = n.slice(4, 8);
        break;
      }
      case 'NEXT':
        S.nextAt = Date.now() + Number(rest) * 1000;
        S.overlay = 'deal';
        break;
      case 'MATCH':
        S.matchWinner = Number(rest.split(/\s+/)[0]);
        S.overlay = 'match';
        if (S.matchWinner === S.mySeat && S.settings.mode !== 'single') sfx('fanfare');
        S.lastResult = { winner: S.matchWinner, names: [...S.tableNames], scores: [...S.scores], mode: S.settings.mode };
        break;
      case 'ERROR':
        if ((S.rejoining || S.reconnecting) && /seat|ended|room/i.test(rest)) {
          giveUpRejoin(rest);
          return;
        }
        if (S.view === 'home') $('#home-error').textContent = rest;
        else toast(rest, true);
        sfx('error');
        return;
      default:
        return;
    }
    render();
  }

  function parseSettings(wire) {
    const s = {};
    wire.trim().split(/\s+/).forEach(pair => {
      const [k, v] = pair.split('=');
      s[k] = v;
    });
    return {
      room: s.room, deck: s.deck, mode: s.mode,
      points: Number(s.points), rounds: Number(s.rounds), timer: Number(s.timer),
      twos: s.twos === 'on', wrap: s.wrap === 'on', every3d: s.every3d === 'on',
      bots: s.bots || 'normal'
    };
  }

  function who(seat) {
    if (seat === S.mySeat) return 'You';
    return S.tableNames[seat] || 'Seat ' + (seat + 1);
  }

  /** A short description of a played combination, e.g. "a pair" or "K♥". */
  function describe(cards) {
    const n = cards.length;
    if (n === 1) return Cards.pretty(cards[0]);
    if (n === 2) return 'a pair';
    if (n === 3) return 'three of a kind';
    if (n !== 5) return n + ' cards';
    const parsed = cards.map(Cards.parse);
    const counts = {};
    parsed.forEach(([r]) => { counts[r] = (counts[r] || 0) + 1; });
    const groups = Object.values(counts).sort((a, b) => b - a);
    if (groups[0] === 4) return 'four of a kind';
    if (groups[0] === 3 && groups[1] === 2) return 'a full house';
    const flush = parsed.every(([, s]) => s === parsed[0][1]);
    const idx = parsed.map(([r]) => Cards.RANKS.indexOf(r)).sort((a, b) => a - b);
    const set = parsed.map(([r]) => r).sort().join(',');
    const straight = groups.length === 5 && (idx[4] - idx[0] === 4
      || set === ['2', '3', '4', '5', 'A'].sort().join(',') || set === ['2', '3', '4', '5', '6'].sort().join(','));
    if (straight && flush) return 'a straight flush';
    if (flush) return 'a flush';
    if (straight) return 'a straight';
    return 'five cards';
  }

  // ---------- Rendering ----------

  function render() {
    $('#home').hidden = S.view !== 'home';
    $('#lobby').hidden = S.view !== 'lobby';
    $('#table').hidden = S.view !== 'table';
    if (S.view === 'lobby') renderLobby();
    if (S.view === 'table') renderTable();
    Sound.music(S.view === 'home' ? null : S.settings.room);
  }

  const isOwner = () => S.owner === S.mySeat;

  function renderLobby() {
    $('#lobby-code').textContent = S.room || '';
    $('#invite-link').value = location.origin + location.pathname + '#' + (S.room || '');

    const winds = ['東', '南', '西', '北'];
    $('#seat-list').innerHTML = S.names.map((n, seat) => {
      const tags = (seat === S.owner ? '<span class="tag">Host</span>' : '') + (seat === S.mySeat ? '<span class="tag you">You</span>' : '');
      if (!n) return `<li class="empty"><span class="medal">${winds[seat]}</span><span class="who"><b>Open seat</b><small>A bot plays here if nobody joins</small></span></li>`;
      return `<li><span class="medal">${esc(n[0].toUpperCase())}</span><span class="who"><b>${esc(n)}</b><small>${S.away.has(seat) ? 'Reconnecting…' : 'Seat ' + (seat + 1)}</small></span>${tags}</li>`;
    }).join('');

    const humans = S.names.filter(Boolean).length;
    const ownerName = S.names[S.owner] || 'the host';
    $('#seat-note').textContent = isOwner()
      ? (humans < 4 ? `${4 - humans} open seat${humans === 3 ? '' : 's'} will be played by bots.` : 'The table is full.')
      : `Waiting for ${ownerName} to start the game.`;
    $('#start-btn').hidden = !isOwner();
    $('#settings').disabled = !isOwner();
    $('#settings-lock').textContent = isOwner() ? 'You are the host' : `Only ${ownerName} can change these`;

    const r = S.lastResult;
    $('#last-result').hidden = !r;
    if (r) {
      const order = [0, 1, 2, 3].sort((a, b) => r.scores[a] - r.scores[b]);
      $('#last-result').innerHTML = `<b>Last game:</b> ${esc(r.names[r.winner] || 'Seat ' + (r.winner + 1))} won` +
        (r.mode === 'single' ? '.' : ' &middot; ' + order.map(s => `${esc(r.names[s] || '?')} ${r.scores[s]}`).join(', '));
    }

    renderPickers();
    const st = S.settings;
    document.querySelectorAll('input[name="mode"]').forEach(i => { i.checked = i.value === st.mode; });
    document.querySelectorAll('input[name="bots"]').forEach(i => { i.checked = i.value === st.bots; });
    $('#bots-note').textContent = BOT_NOTES[st.bots] || '';
    $('#mode-points').hidden = st.mode !== 'points';
    $('#mode-rounds').hidden = st.mode !== 'rounds';
    $('#mode-single').hidden = st.mode !== 'single';
    if (document.activeElement !== $('#points-input')) $('#points-input').value = st.points;
    if (document.activeElement !== $('#rounds-input')) $('#rounds-input').value = st.rounds;
    const timer = $('#timer-select');
    if (![...timer.options].some(o => Number(o.value) === st.timer)) {
      timer.add(new Option(st.timer + ' seconds', st.timer));
    }
    timer.value = String(st.timer);
    $('#rule-every3d').checked = st.every3d;
    $('#rule-twos').checked = st.twos;
    $('#rule-wrap').checked = st.wrap;
  }

  let pickerKey = '';
  function renderPickers() {
    const key = S.settings.room + '|' + S.settings.deck;
    if (key === pickerKey) return;
    pickerKey = key;
    $('#room-picker').innerHTML = ROOM_IDS.map(id => {
      const rm = Rooms.ROOMS[id];
      const on = S.settings.room === id;
      return `<label class="pick${on ? ' on' : ''}"><input type="radio" name="room" value="${id}"${on ? ' checked' : ''}>${Rooms.preview(id, rm.deck)}<span>${esc(rm.name)}</span><small>${esc(rm.blurb)}</small></label>`;
    }).join('');
    $('#deck-picker').innerHTML = DECK_IDS.map(id => {
      const on = S.settings.deck === id;
      const fan = [['AS', -14, -18], ['KH', 0, 0], ['2D', 14, 18]].map(([c, a, dx]) =>
        Cards.card(id, c).replace('<svg class="card"', `<svg class="card" style="transform:translateX(calc(-50% + ${dx}px)) rotate(${a}deg)"`)).join('');
      const paired = Rooms.ROOMS[S.settings.room].deck === id ? ' · matches room' : '';
      return `<label class="pick${on ? ' on' : ''}"><input type="radio" name="deck" value="${id}"${on ? ' checked' : ''}><div class="fan" aria-hidden="true">${fan}</div><span>${esc(Cards.DECKS[id].name)}</span><small>${DECK_NOTES[id]}${paired}</small></label>`;
    }).join('');
  }

  function mustIncludeThreeOfDiamonds() {
    return S.playsThisDeal === 0 && (S.deal === 1 || S.settings.every3d) && S.hand.includes('3D');
  }

  function tableView() {
    const playing = S.phase === 'playing';
    const myTurn = playing && S.turn === S.mySeat;
    const players = [0, 1, 2, 3].map(rel => {
      const seat = (S.mySeat + rel) % 4;
      return { seat, name: who(seat) + (S.away.has(seat) ? ' \u00B7 away' : ''), count: S.counts[seat], active: playing && S.turn === seat };
    });

    let label = S.table.length ? `${who(S.tableOwner)} played ${describe(S.table)}` : S.lastAction;
    let status = '';
    if (myTurn) {
      if (mustIncludeThreeOfDiamonds()) status = 'Your turn: your play must include 3♦';
      else if (S.leading) status = 'Your turn: lead anything';
      else status = `Your turn: beat ${describe(S.table)} or pass`;
    } else if (playing && S.turn >= 0) {
      status = (S.lastPass ? S.lastPass + ' · ' : '') + `${who(S.turn)} to play`;
    } else if (S.dealWinner >= 0) {
      status = `${who(S.dealWinner)} ${S.dealWinner === S.mySeat ? 'win' : 'wins'} the deal` +
        (revealing() ? ' · tap for results' : '');
    }
    if (!label && playing && S.leading && !myTurn) label = 'Table is clear';

    return {
      deck: S.settings.deck, players, tableCards: S.table, label, status, chips: quickChips(),
      hand: S.hand.map(code => ({ code, selected: S.selected.has(code) })),
      canPlay: myTurn && S.selected.size > 0,
      canPass: myTurn && !S.leading,
      anim: liveAnimations()
    };
  }

  const rulesNow = () => ({ twos: S.settings.twos, wrap: S.settings.wrap });
  const tableToBeat = () => (S.leading ? null : S.table);

  /** Combinations available right now, by type, weakest first. */
  function quickOptions() {
    return Combos.options(S.hand, tableToBeat(), rulesNow(), mustIncludeThreeOfDiamonds());
  }

  /** Chips for the combos you can make; singles only when the table holds a single. */
  function quickChips() {
    if (S.phase !== 'playing' || !S.hand.length) return [];
    const opts = quickOptions();
    const table = tableToBeat();
    return Combos.TYPES
      .filter(t => opts[t] && opts[t].length && (t !== 'single' || (table && table.length === 1)))
      .map(t => ({ type: t, label: Combos.LABELS[t], count: opts[t].length, active: !!(S.quick && S.quick.type === t) }));
  }

  /** Selects the smallest combo of a type; tapping the same chip again steps to the next one up. */
  function quickSelect(type) {
    const list = quickOptions()[type] || [];
    if (!list.length) return;
    const index = S.quick && S.quick.type === type ? (S.quick.index + 1) % list.length : 0;
    S.quick = { type, index };
    S.selected = new Set(list[index].cards.map(c => c.code));
    sfx('select');
    renderTable();
  }

  function renderTable() {
    $('#stage').innerHTML = Rooms.scene(S.settings.room, tableView(), orientation());
    $('#stage').style.background = Rooms.ROOMS[S.settings.room].edge;
    renderHud();
    renderOverlay();
    tick();
  }

  function modeLine() {
    const st = S.settings;
    if (st.mode === 'points') return `Race to ${st.points} · deal ${S.deal}`;
    if (st.mode === 'rounds') return `Deal ${S.deal} of ${st.rounds}`;
    return 'Single deal';
  }

  function renderHud() {
    const showScores = S.settings.mode !== 'single';
    const board = showScores ? `<div class="chip scoreboard" aria-label="Scores">${[0, 1, 2, 3].map(seat =>
      `<span class="${seat === S.mySeat ? 'me' : ''}">${esc(who(seat))}</span><span class="${seat === S.mySeat ? 'me' : ''}">${S.scores[seat]}</span>`).join('')}</div>` : '';
    $('#hud').innerHTML =
      `<div class="hud-left"><div class="chip"><span class="code">${esc(S.room || '')}</span> &middot; ${modeLine()}</div></div>` +
      `<div class="hud-right">${board}<div class="chip"><button type="button" class="btn ghost react-btn" id="react-btn" aria-label="Send a reaction" aria-haspopup="true">😀</button><button type="button" class="btn ghost" data-open-sound aria-label="Sound settings">Sound</button><button type="button" class="btn ghost" id="leave-btn">${S.leaveArmed ? 'Leave game? A bot takes your seat' : 'Leave'}</button></div></div>`;
  }

  function revealing() {
    return S.overlay && Date.now() < (S.revealUntil || 0);
  }

  function renderOverlay() {
    const ov = $('#overlay');
    if (!S.overlay || revealing()) { ov.hidden = true; ov.innerHTML = ''; return; }
    ov.hidden = false;
    const rows = [0, 1, 2, 3].sort((a, b) => S.scores[a] - S.scores[b]);
    const showTotals = S.settings.mode !== 'single';
    const table = `<table><thead><tr><th>Player</th><th class="num">This deal</th>${showTotals ? '<th class="num">Total</th>' : ''}</tr></thead><tbody>` +
      rows.map(seat => `<tr class="${(S.overlay === 'match' ? seat === S.matchWinner : seat === S.dealWinner) ? 'win' : ''}${S.reveal[seat] && S.reveal[seat].length ? ' has-cards' : ''}"><td>${esc(who(seat))}${seat === S.dealWinner ? '<small class="went-out">Went out</small>' : ''}</td><td class="num">${S.penalties[seat] ? '+' + S.penalties[seat] : '0'}</td>${showTotals ? `<td class="num">${S.scores[seat]}</td>` : ''}</tr>` +
        leftoverCards(seat, showTotals ? 3 : 2)).join('') +
      '</tbody></table>';

    if (S.overlay === 'deal') {
      ov.innerHTML = `<div class="result" role="dialog" aria-label="Deal results"><div class="eyebrow">${modeLine()}</div>` +
        `<h2>${esc(who(S.dealWinner))} ${S.dealWinner === S.mySeat ? 'win' : 'wins'} the deal</h2>${table}` +
        `<div class="foot"><span class="muted">Penalty: 1 point per card left, doubled at 10+, tripled at 13.</span><span id="next-count"></span></div></div>`;
    } else {
      const title = S.settings.mode === 'single'
        ? `${esc(who(S.matchWinner))} ${S.matchWinner === S.mySeat ? 'win' : 'wins'}!`
        : `${esc(who(S.matchWinner))} ${S.matchWinner === S.mySeat ? 'win' : 'wins'} the game`;
      ov.innerHTML = `<div class="result" role="dialog" aria-label="Game results"><div class="eyebrow">Game over</div><h2>${title}</h2>${table}` +
        (isOwner()
          ? `<div class="foot"><span class="muted">Same room, players and settings.</span><span class="row">` +
            `<button type="button" class="btn" id="to-lobby-btn">Change settings</button>` +
            `<button type="button" class="btn primary" id="play-again-btn"${S.restarting ? ' disabled' : ''}>${S.restarting ? 'Starting…' : 'Play again'}</button></span></div></div>`
          : `<div class="foot"><span class="muted">Waiting for ${esc(S.names[S.owner] || 'the host')} to start the next game.</span>` +
            `<button type="button" class="btn" id="to-lobby-btn">Back to lobby</button></div></div>`);
    }
  }

  /** The cards a player was left holding when the deal ended. */
  /** A small card tile with a big rank and suit, easy to read on a phone. */
  function tile(code) {
    const [r, s] = Cards.parse(code);
    const red = s === 'D' || s === 'H';
    return `<span class="tile${red ? ' red' : ''}" aria-label="${Cards.pretty(code)}"><b>${r}</b><i>${Cards.pretty(code).slice(r.length)}</i></span>`;
  }

  function leftoverCards(seat, cols) {
    const cards = S.reveal[seat] || [];
    if (!cards.length) return '';
    return `<tr class="left-row"><td colspan="${cols}"><div class="left-cards" aria-label="${cards.length} cards left">${cards.map(tile).join('')}</div></td></tr>`;
  }

  let lastTickSecond = 0;
  function tick() {
    if (S.view !== 'table') return;
    const layer = document.getElementById('timer-layer');
    if (layer) {
      if (S.turnDeadline && S.phase === 'playing' && S.turn >= 0) {
        const left = Math.max(0, S.turnDeadline - Date.now());
        const rel = (S.turn - S.mySeat + 4) % 4;
        layer.innerHTML = Rooms.timerBar(S.settings.room, rel, left / (S.turnSeconds * 1000), Math.ceil(left / 1000), orientation());
      } else {
        layer.innerHTML = '';
      }
    }
    if (S.turnDeadline && S.phase === 'playing' && S.turn === S.mySeat) {
      const secs = Math.ceil((S.turnDeadline - Date.now()) / 1000);
      if (secs <= 5 && secs > 0 && secs !== lastTickSecond) { lastTickSecond = secs; sfx('tick'); }
    }
    const next = document.getElementById('next-count');
    if (next) next.textContent = `Next deal in ${Math.max(0, Math.ceil((S.nextAt - Date.now()) / 1000))}s`;
  }
  setInterval(tick, 200);

  // ---------- Toasts ----------

  function toast(text, error) {
    const el = document.createElement('div');
    el.className = 'toast' + (error ? ' error' : '');
    el.textContent = text;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), error ? 5000 : 3200);
  }

  function esc(s) {
    return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  }

  // ---------- Events ----------

  function playerName() {
    const name = $('#name-input').value.trim().replace(/\s+/g, '_');
    if (!/^[\p{L}\p{N}_.-]{1,16}$/u.test(name)) {
      $('#home-error').textContent = 'Enter a name: letters, numbers, - _ . and up to 16 characters.';
      $('#name-input').focus();
      return null;
    }
    try { localStorage.setItem('big2-name', name); } catch (e) { /* storage unavailable */ }
    return name;
  }

  $('#create-form').addEventListener('submit', e => {
    e.preventDefault();
    $('#home-error').textContent = '';
    const name = playerName();
    if (name) send('CREATE ' + name);
  });

  $('#join-form').addEventListener('submit', e => {
    e.preventDefault();
    $('#home-error').textContent = '';
    const code = $('#code-input').value.trim().toUpperCase();
    if (!/^[A-Z0-9_-]{1,12}$/.test(code)) {
      $('#home-error').textContent = 'Enter the room code your friend sent you.';
      $('#code-input').focus();
      return;
    }
    const name = playerName();
    if (name) send(`JOIN ${code} ${name}`);
  });

  $('#copy-btn').addEventListener('click', () => {
    const input = $('#invite-link');
    const done = () => toast('Invite link copied');
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(input.value).then(done, () => { input.select(); });
    } else {
      input.select();
      try { document.execCommand('copy') ? done() : null; } catch (e) { /* user can copy the selected text */ }
    }
  });

  $('#start-btn').addEventListener('click', () => send('START'));
  $('#leave-lobby-btn').addEventListener('click', leaveRoom);

  $('#room-picker').addEventListener('change', e => { if (e.target.name === 'room') send('SET room ' + e.target.value); });
  $('#deck-picker').addEventListener('change', e => { if (e.target.name === 'deck') send('SET deck ' + e.target.value); });
  document.querySelectorAll('input[name="mode"]').forEach(i => i.addEventListener('change', () => send('SET mode ' + i.value)));
  document.querySelectorAll('input[name="bots"]').forEach(i => i.addEventListener('change', () => send('SET bots ' + i.value)));
  $('#points-input').addEventListener('change', e => send('SET points ' + e.target.value));
  $('#rounds-input').addEventListener('change', e => send('SET rounds ' + e.target.value));
  $('#timer-select').addEventListener('change', e => send('SET timer ' + e.target.value));
  $('#rule-every3d').addEventListener('change', e => send('SET every3d ' + (e.target.checked ? 'on' : 'off')));
  $('#rule-twos').addEventListener('change', e => send('SET twos ' + (e.target.checked ? 'on' : 'off')));
  $('#rule-wrap').addEventListener('change', e => send('SET wrap ' + (e.target.checked ? 'on' : 'off')));

  function playSelected() {
    if (!S.selected.size || S.turn !== S.mySeat || S.phase !== 'playing') return;
    const why = Combos.problem([...S.selected], tableToBeat(), rulesNow(), mustIncludeThreeOfDiamonds());
    if (why) {
      animate('shake');
      sfx('error');
      toast(why, true);
      renderTable();
      return;
    }
    send('PLAY ' + [...S.selected].join(' '));
  }

  $('#table').addEventListener('click', e => {
    if (revealing() && !e.target.closest('#hud')) {
      S.revealUntil = 0;
      renderTable();
      return;
    }
    const quick = e.target.closest('[data-quick]');
    if (quick) {
      quickSelect(quick.getAttribute('data-quick'));
      return;
    }
    const reaction = e.target.closest('[data-react]');
    if (reaction) {
      send('REACT ' + reaction.getAttribute('data-react'));
      $('#react-tray').hidden = true;
      return;
    }
    if (e.target.closest('#react-btn')) {
      $('#react-tray').hidden = !$('#react-tray').hidden;
      return;
    }
    if (!$('#react-tray').hidden && !e.target.closest('#react-tray')) $('#react-tray').hidden = true;
    const card = e.target.closest('[data-card]');
    if (card) {
      S.quick = null;
      const code = card.getAttribute('data-card');
      if (S.selected.has(code)) S.selected.delete(code); else S.selected.add(code);
      sfx('select');
      renderTable();
      return;
    }
    const action = e.target.closest('[data-action]');
    if (action) {
      const a = action.getAttribute('data-action');
      if (a === 'play') playSelected();
      if (a === 'pass') send('PASS');
      return;
    }
    if (e.target.id === 'leave-btn') {
      if (S.leaveArmed) leaveRoom();
      else { S.leaveArmed = true; renderHud(); setTimeout(() => { S.leaveArmed = false; if (S.view === 'table') renderHud(); }, 4000); }
      return;
    }
    if (e.target.id === 'play-again-btn') {
      playAgain();
      return;
    }
    if (e.target.id === 'to-lobby-btn') {
      S.overlay = null;
      S.tableNames = [...S.names];
      S.view = S.phase === 'playing' ? 'table' : 'lobby';
      render();
    }
  });

  // Re-draw the table when the phone turns or the window changes shape.
  let lastOrientation = orientation(), resizeTimer = 0;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (orientation() !== lastOrientation) {
        lastOrientation = orientation();
        if (S.view === 'table') renderTable();
      }
    }, 120);
  });

  document.addEventListener('keydown', e => {
    if (S.view !== 'table' || e.target.matches('input, select, textarea')) return;
    if (e.key === 'Enter' && S.overlay === 'match') playAgain();
    else if (e.key === 'Enter') playSelected();
    else if (e.key === 'p' || e.key === 'P') { if (S.turn === S.mySeat && !S.leading) send('PASS'); }
    else if (e.key === 'Escape' && $('#sound-dialog').hidden) { S.selected.clear(); renderTable(); }
  });

  function playAgain() {
    if (!isOwner() || S.restarting) return;
    S.restarting = true;
    renderOverlay();
    send('START');
    // If the server refuses (for example someone is mid-join), let the host try again.
    setTimeout(() => { if (S.restarting) { S.restarting = false; if (S.view === 'table') renderOverlay(); } }, 4000);
  }

  function leaveRoom() {
    leaving = true;
    forgetSeat();
    clearTimeout(reconnectTimer);
    S.reconnecting = false;
    showReconnecting();
    if (ws && ws.readyState === WebSocket.OPEN) ws.send('QUIT');
    if (ws) ws.close();
    history.replaceState(null, '', location.pathname);
    Object.assign(S, { view: 'home', room: null, key: null, owner: -1, names: [null, null, null, null], tableNames: [null, null, null, null], hand: [], selected: new Set(), overlay: null, lastResult: null, phase: 'lobby', leaveArmed: false, away: new Set() });
    pickerKey = '';
    render();
  }

  // ---------- Sound settings ----------

  // Browsers only allow audio after a tap or key press, so every interaction (re)starts it.
  ['pointerdown', 'keydown'].forEach(type => document.addEventListener(type, () => Sound.unlock(), true));

  function syncSoundControls() {
    const p = Sound.prefs();
    $('#music-on').checked = p.music;
    $('#sfx-on').checked = p.sfx;
    $('#music-vol').value = Math.round(p.musicVol * 100);
    $('#sfx-vol').value = Math.round(p.sfxVol * 100);
    $('#music-vol').disabled = !p.music;
    $('#sfx-vol').disabled = !p.sfx;
    const room = S.view === 'home' ? null : Rooms.ROOMS[S.settings.room];
    $('#now-playing').textContent = room
      ? `Now playing: ${Sound.title(S.settings.room)} (${room.name})`
      : 'Each room has its own jazz band. Music starts when you join a room.';
  }

  function openSound() {
    syncSoundControls();
    $('#sound-dialog').hidden = false;
    $('#sound-done').focus();
  }
  function closeSound() {
    $('#sound-dialog').hidden = true;
  }

  document.addEventListener('click', e => {
    if (e.target.closest('[data-open-sound]')) openSound();
  });
  $('#sound-done').addEventListener('click', closeSound);
  $('#sound-dialog').addEventListener('click', e => { if (e.target.id === 'sound-dialog') closeSound(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && !$('#sound-dialog').hidden) closeSound(); });

  $('#music-on').addEventListener('change', e => { Sound.set({ music: e.target.checked }); syncSoundControls(); });
  $('#sfx-on').addEventListener('change', e => {
    Sound.set({ sfx: e.target.checked });
    syncSoundControls();
    sfx('click');
  });
  $('#music-vol').addEventListener('input', e => Sound.set({ musicVol: e.target.value / 100 }));
  $('#sfx-vol').addEventListener('input', e => Sound.set({ sfxVol: e.target.value / 100 }));
  $('#sfx-vol').addEventListener('change', () => sfx('play', 2));

  // ---------- Installing as an app (PWA) ----------

  const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  let installPrompt = null;

  if ('serviceWorker' in navigator && window.isSecureContext) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => { /* the game still works without it */ });
    });
  }

  // Chrome, Edge and Android offer an install prompt we can trigger from our own button.
  window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
    installPrompt = e;
    $('#install').hidden = false;
    $('#install-btn').hidden = false;
  });
  $('#install-btn').addEventListener('click', async () => {
    if (!installPrompt) return;
    installPrompt.prompt();
    await installPrompt.userChoice;
    installPrompt = null;
    $('#install').hidden = true;
  });
  window.addEventListener('appinstalled', () => {
    $('#install').hidden = true;
    toast('Big 2 is installed. Open it from your home screen.');
  });

  // iPhone and iPad have no install prompt, so explain the Share menu instead.
  const iOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  if (iOS && !standalone) {
    $('#install').hidden = false;
    $('#install-ios').hidden = false;
  }

  // ---------- Start ----------

  try { $('#name-input').value = localStorage.getItem('big2-name') || ''; } catch (e) { /* storage unavailable */ }
  const fromLink = location.hash.slice(1).toUpperCase();
  if (fromLink) {
    $('#code-input').value = fromLink;
    $('#create-btn').classList.remove('primary');
    $('#join-form button').classList.add('primary');
  }
  ($('#name-input').value ? (fromLink ? $('#join-form button') : $('#create-btn')) : $('#name-input')).focus();
  // Developer aid: with ?debug in the URL, window.Big2Debug.feed('LINE') replays a server message locally.
  if (new URLSearchParams(location.search).has('debug')) {
    window.Big2Debug = { feed: handle, state: S };
  } else {
    const saved = loadSeat();
    if (saved && (!fromLink || fromLink === saved.room)) {
      S.room = saved.room;
      S.key = saved.key;
      S.rejoining = true;
      $('#home-error').textContent = `Rejoining room ${saved.room}\u2026`;
      rejoin();
    } else {
      connect();
    }
  }
  render();
})();
