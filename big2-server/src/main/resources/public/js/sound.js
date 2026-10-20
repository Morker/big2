// Sound for Big 2, made entirely with the Web Audio API (no audio files):
// synthesized effects for every move, and a small generative jazz band for each room.
//
// Browsers only allow sound after the player interacts, so app.js calls Sound.unlock() on the first tap.
(function () {
  const KEY = 'big2-sound';
  const prefs = Object.assign({ music: true, musicVol: 0.5, sfx: true, sfxVol: 0.8 }, readPrefs());

  function readPrefs() {
    try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; }
  }
  function savePrefs() {
    try { localStorage.setItem(KEY, JSON.stringify(prefs)); } catch (e) { /* storage unavailable */ }
  }

  let ctx = null, musicBus, sfxBus, reverbIn, noiseBuf;
  let unlocked = false, band = null, wantedRoom = null;
  const mtof = m => 440 * Math.pow(2, (m - 69) / 12);
  const rand = (a, b) => a + Math.random() * (b - a);
  const pick = list => list[Math.floor(Math.random() * list.length)];

  // ---------- Audio graph ----------

  function init() {
    if (ctx) return true;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;
    ctx = new AC();
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -14; comp.knee.value = 12; comp.ratio.value = 3;
    comp.attack.value = 0.004; comp.release.value = 0.25;
    comp.connect(ctx.destination);
    musicBus = ctx.createGain();
    sfxBus = ctx.createGain();
    musicBus.connect(comp);
    sfxBus.connect(comp);

    // A small generated "club" reverb for the band.
    const rev = ctx.createConvolver();
    rev.buffer = impulse(2.4, 3.2);
    const revOut = ctx.createGain();
    revOut.gain.value = 0.3;
    rev.connect(revOut);
    revOut.connect(musicBus);
    reverbIn = rev;

    noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
    const d = noiseBuf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    applyVolumes(true);
    return true;
  }

  function impulse(seconds, decay) {
    const len = Math.floor(ctx.sampleRate * seconds);
    const buf = ctx.createBuffer(2, len, ctx.sampleRate);
    for (let ch = 0; ch < 2; ch++) {
      const data = buf.getChannelData(ch);
      for (let i = 0; i < len; i++) data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay);
    }
    return buf;
  }

  function applyVolumes(instant) {
    if (!ctx) return;
    const t = ctx.currentTime;
    musicBus.gain.setTargetAtTime(prefs.music ? prefs.musicVol * 0.7 : 0, t, instant ? 0.001 : 0.08);
    sfxBus.gain.setTargetAtTime(prefs.sfx ? prefs.sfxVol : 0, t, instant ? 0.001 : 0.05);
  }

  const running = () => ctx && unlocked && ctx.state === 'running';

  // ---------- Building blocks ----------

  function osc(type, freq, t, stop) {
    const o = ctx.createOscillator();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    o.start(t);
    o.stop(stop);
    return o;
  }
  function noise(t, stop) {
    const s = ctx.createBufferSource();
    s.buffer = noiseBuf;
    s.loop = true;
    s.start(t, Math.random() * 1.5);
    s.stop(stop);
    return s;
  }
  function filter(type, freq, q) {
    const f = ctx.createBiquadFilter();
    f.type = type;
    f.frequency.value = freq;
    if (q != null) f.Q.value = q;
    return f;
  }
  function gain(v) {
    const g = ctx.createGain();
    g.gain.value = v == null ? 1 : v;
    return g;
  }
  /** Attack to peak, glide to a sustain level until t + hold, then release. Returns when it ends. */
  function shape(g, t, attack, peak, sustain, hold, release) {
    const p = g.gain, h = Math.max(hold, attack + 0.01);
    p.setValueAtTime(0.0001, t);
    p.exponentialRampToValueAtTime(Math.max(peak, 0.0002), t + attack);
    p.exponentialRampToValueAtTime(Math.max(sustain, 0.0001), t + h);
    p.exponentialRampToValueAtTime(0.0001, t + h + release);
    return t + h + release + 0.05;
  }
  /** Sends a voice to its destination, plus some reverb. */
  function route(node, dest, wet) {
    node.connect(dest);
    if (wet) {
      const send = gain(wet);
      node.connect(send);
      send.connect(reverbIn);
    }
  }

  // ---------- Band instruments: (dest, t, midi, seconds, velocity) ----------

  const INSTRUMENTS = {
    piano(dest, t, m, dur, v) {
      const f = mtof(m), g = gain(0), lp = filter('lowpass', 3200, 0.5);
      const end = shape(g, t, 0.003, 0.15 * v, 0.03 * v, Math.min(dur, 0.7), 0.35);
      lp.frequency.setValueAtTime(4200, t);
      lp.frequency.exponentialRampToValueAtTime(1400, t + 0.6);
      const a = osc('triangle', f, t, end), b = osc('sine', f * 2, t, end), bg = gain(0.25);
      a.connect(lp); b.connect(bg); bg.connect(lp); lp.connect(g);
      route(g, dest, 0.25);
    },
    rhodes(dest, t, m, dur, v) {
      const f = mtof(m), g = gain(0);
      const end = shape(g, t, 0.006, 0.15 * v, 0.05 * v, dur, 0.6);
      const car = osc('sine', f, t, end), mod = osc('sine', f, t, end), depth = gain(0);
      depth.gain.setValueAtTime(f * 1.4 * v, t);
      depth.gain.exponentialRampToValueAtTime(f * 0.04, t + 0.5);
      mod.connect(depth); depth.connect(car.frequency);
      car.connect(g);
      route(g, dest, 0.4);
    },
    vibes(dest, t, m, dur, v) {
      const f = mtof(m), g = gain(0), trem = gain(0.8);
      const end = shape(g, t, 0.004, 0.13 * v, 0.05 * v, Math.min(dur, 0.6), 1.3);
      const a = osc('sine', f, t, end), b = osc('sine', f * 4, t, end), bg = gain(0.12);
      const lfo = osc('sine', 5.5, t, end), lfoDepth = gain(0.22);
      lfo.connect(lfoDepth); lfoDepth.connect(trem.gain);
      a.connect(trem); b.connect(bg); bg.connect(trem); trem.connect(g);
      route(g, dest, 0.45);
    },
    bass(dest, t, m, dur, v) {
      const f = mtof(m), g = gain(0), lp = filter('lowpass', 650, 0.7);
      const end = shape(g, t, 0.008, 0.36 * v, 0.12 * v, dur, 0.12);
      const a = osc('sine', f, t, end), b = osc('triangle', f, t, end), bg = gain(0.45);
      a.connect(lp); b.connect(bg); bg.connect(lp); lp.connect(g);
      route(g, dest, 0.08);
    },
    clarinet(dest, t, m, dur, v) {
      const f = mtof(m), g = gain(0), lp = filter('lowpass', f * 2.6, 0.8);
      const end = shape(g, t, 0.05, 0.075 * v, 0.06 * v, dur, 0.12);
      const o = osc('square', f, t, end), vib = osc('sine', 5, t, end), vd = gain(0);
      vd.gain.setValueAtTime(0, t);
      vd.gain.linearRampToValueAtTime(f * 0.007, t + 0.25);
      vib.connect(vd); vd.connect(o.frequency);
      o.connect(lp); lp.connect(g);
      route(g, dest, 0.35);
    },
    pluck(dest, t, m, dur, v) {
      // Guzheng-like: a bright pluck with a small upward scoop into the note.
      const f = mtof(m), g = gain(0), lp = filter('lowpass', 5000, 0.6);
      const end = shape(g, t, 0.002, 0.2 * v, 0.02 * v, Math.max(dur, 0.8), 0.5);
      lp.frequency.setValueAtTime(5000, t);
      lp.frequency.exponentialRampToValueAtTime(1300, t + 0.6);
      const a = osc('triangle', f * 0.97, t, end), b = osc('sine', f * 2 * 0.97, t, end), bg = gain(0.3);
      a.frequency.exponentialRampToValueAtTime(f, t + 0.06);
      b.frequency.exponentialRampToValueAtTime(f * 2, t + 0.06);
      a.connect(lp); b.connect(bg); bg.connect(lp); lp.connect(g);
      route(g, dest, 0.4);
    },
    flute(dest, t, m, dur, v) {
      const f = mtof(m), g = gain(0);
      const end = shape(g, t, 0.07, 0.09 * v, 0.075 * v, dur, 0.15);
      const o = osc('sine', f, t, end), vib = osc('sine', 5.2, t, end), vd = gain(0);
      vd.gain.setValueAtTime(0, t);
      vd.gain.linearRampToValueAtTime(f * 0.006, t + 0.3);
      vib.connect(vd); vd.connect(o.frequency);
      const breath = noise(t, end), bp = filter('bandpass', f * 1.5, 3), bgn = gain(0.05);
      breath.connect(bp); bp.connect(bgn); bgn.connect(g);
      o.connect(g);
      route(g, dest, 0.4);
    },
    guitar(dest, t, m, dur, v) {
      const f = mtof(m), g = gain(0), lp = filter('lowpass', 1900, 0.5);
      const end = shape(g, t, 0.003, 0.16 * v, 0.03 * v, Math.min(dur, 0.5), 0.35);
      const a = osc('triangle', f, t, end), b = osc('sine', f * 2, t, end), bg = gain(0.18);
      a.connect(lp); b.connect(bg); bg.connect(lp); lp.connect(g);
      route(g, dest, 0.3);
    }
  };

  // ---------- Band drums: (dest, t, velocity) ----------

  function noiseHit(dest, t, type, freq, q, attack, peak, decay, wet) {
    const g = gain(0), f = filter(type, freq, q);
    const end = shape(g, t, attack, peak, 0.0001, attack + 0.005, decay);
    const n = noise(t, end);
    n.connect(f); f.connect(g);
    route(g, dest, wet);
  }
  const DRUMS = {
    ride: (d, t, v) => noiseHit(d, t, 'highpass', 6500, 0.7, 0.001, 0.06 * v, 0.32, 0.2),
    chick: (d, t, v) => noiseHit(d, t, 'highpass', 7500, 0.7, 0.001, 0.035 * v, 0.04, 0),
    brush: (d, t, v) => noiseHit(d, t, 'bandpass', 2300, 0.6, 0.018, 0.05 * v, 0.17, 0.15),
    shaker: (d, t, v) => noiseHit(d, t, 'bandpass', 6200, 1, 0.006, 0.022 * v, 0.05, 0),
    kick(d, t, v) {
      const g = gain(0), end = shape(g, t, 0.003, 0.3 * v, 0.0001, 0.01, 0.2);
      const o = osc('sine', 95, t, end);
      o.frequency.exponentialRampToValueAtTime(45, t + 0.11);
      o.connect(g); route(g, d, 0);
    },
    rim(d, t, v) {
      const g = gain(0), end = shape(g, t, 0.001, 0.09 * v, 0.0001, 0.005, 0.035);
      osc('triangle', 1750, t, end).connect(g);
      route(g, d, 0.1);
    },
    block(d, t, v) {
      const g = gain(0), end = shape(g, t, 0.001, 0.12 * v, 0.0001, 0.005, 0.14);
      osc('sine', 820, t, end).connect(g);
      const h = gain(0.4);
      osc('sine', 1230, t, end).connect(h); h.connect(g);
      route(g, d, 0.35);
    }
  };

  // ---------- Harmony ----------

  const PC = { C: 0, Db: 1, 'C#': 1, D: 2, Eb: 3, E: 4, F: 5, 'F#': 6, Gb: 6, G: 7, Ab: 8, A: 9, Bb: 10, B: 11 };
  const QUALITY = {
    maj7: { tones: [0, 4, 7, 11], scale: [0, 2, 4, 7, 9, 11] },
    maj9: { tones: [0, 4, 7, 11, 14], scale: [0, 2, 4, 7, 9, 11] },
    '6': { tones: [0, 4, 7, 9], scale: [0, 2, 4, 7, 9] },
    m7: { tones: [0, 3, 7, 10], scale: [0, 2, 3, 5, 7, 10] },
    m9: { tones: [0, 3, 7, 10, 14], scale: [0, 2, 3, 5, 7, 10] },
    m6: { tones: [0, 3, 7, 9], scale: [0, 2, 3, 7, 9] },
    '7': { tones: [0, 4, 7, 10], scale: [0, 2, 4, 7, 9, 10] },
    m7b5: { tones: [0, 3, 6, 10], scale: [0, 3, 5, 6, 10] }
  };
  function parseChord(sym) {
    const m = /^([A-G][b#]?)(.*)$/.exec(sym);
    const q = QUALITY[m[2]] ? m[2] : (m[2] === '' ? 'maj7' : '7');
    return { root: PC[m[1]], tones: QUALITY[q].tones, scale: QUALITY[q].scale };
  }
  function parseProgression(text) {
    return text.split('|').map(bar => bar.trim().split(/\s+/).map(parseChord));
  }
  /** The MIDI note with pitch class pc nearest to `near`, kept inside [lo, hi]. */
  function nearest(pc, near, lo, hi) {
    let best = null;
    for (let m = lo; m <= hi; m++) {
      if (((m % 12) + 12) % 12 === pc && (best == null || Math.abs(m - near) < Math.abs(best - near))) best = m;
    }
    return best == null ? lo + pc : best;
  }
  /** A compact rootless voicing around the middle of the keyboard. */
  function voicing(ch, withRoot) {
    const tones = withRoot || ch.tones.length < 4 ? ch.tones : ch.tones.slice(1);
    return tones.map(i => nearest((ch.root + i) % 12, 60, 52, 67)).sort((a, b) => a - b);
  }

  // ---------- The room bands ----------

  const STYLES = {
    'gold-room': {
      title: 'Midnight Swing in B♭', bpm: 100, swing: 0.64,
      prog: 'Bbmaj7 | G7 | Cm7 | F7 | Dm7 | G7 | Cm7 F7 | Bbmaj7 F7',
      bass: 'walk', comp: 'swing', compWith: 'piano', drums: 'swing', lead: 'vibes', density: 0.45, range: [67, 84]
    },
    library: {
      title: 'Fireside Ballad in E♭', bpm: 66, swing: 0.6,
      prog: 'Ebmaj7 | Cm7 | Fm7 | Bb7 | Gm7 C7 | Fm7 Bb7 | Ebmaj7 Ab7 | Gm7 C7',
      bass: 'two', comp: 'pads', compWith: 'rhodes', drums: 'brushes', lead: 'rhodes', density: 0.32, range: [65, 82]
    },
    salon: {
      title: 'Riviera Stomp in F', bpm: 132, swing: 0.66,
      prog: 'F6 | D7 | G7 | C7 | F6 | F7 | Bb6 Bbm6 | F6 C7',
      bass: 'two', comp: 'stride', compWith: 'piano', drums: 'hot', lead: 'clarinet', density: 0.55, range: [65, 84]
    },
    'tea-house': {
      title: 'Jasmine Blues in D minor', bpm: 80, swing: 0.56,
      prog: 'Dm9 | Gm7 | Bbmaj7 | A7 | Dm9 | Fmaj7 | Gm7 | A7',
      bass: 'two', comp: 'arp', compWith: 'pluck', drums: 'block', lead: 'pluck', density: 0.4, range: [62, 81],
      pentatonic: [2, 5, 7, 9, 0]
    },
    studio: {
      title: 'Blueprint Bossa in C', bpm: 124, swing: 0.5,
      prog: 'Cmaj7 | Cmaj7 | Dm7 | G7 | Em7 | A7 | Dm7 | G7',
      bass: 'bossa', comp: 'bossa', compWith: 'guitar', drums: 'bossa', lead: 'flute', density: 0.35, range: [67, 86]
    }
  };

  const SWING_COMP = [[0, 5], [1, 4], [3, 6], [2, 7], [0, 3, 6], [1, 5]];

  class Band {
    constructor(roomId) {
      this.room = roomId;
      this.style = STYLES[roomId] || STYLES['gold-room'];
      this.bars = parseProgression(this.style.prog);
      this.out = gain(0.0001);
      this.out.connect(musicBus);
      this.out.gain.setValueAtTime(0.0001, ctx.currentTime);
      this.out.gain.exponentialRampToValueAtTime(1, ctx.currentTime + 1.6);
      this.bar = 0;
      this.step = 0;
      this.next = ctx.currentTime + 0.12;
      this.bassNote = 43;
      this.leadNote = Math.round((this.style.range[0] + this.style.range[1]) / 2);
      this.leadUntil = 0;
      this.timer = setInterval(() => this.pump(), 30);
    }

    stepLength(step) {
      const pair = 60 / this.style.bpm;
      return step % 2 === 0 ? pair * this.style.swing : pair * (1 - this.style.swing);
    }

    pump() {
      if (ctx.state !== 'running') return;
      if (this.next < ctx.currentTime) this.next = ctx.currentTime + 0.05; // recovered from a pause
      while (this.next < ctx.currentTime + 0.25) {
        this.play(this.bar, this.step, this.next);
        this.next += this.stepLength(this.step);
        if (++this.step === 8) { this.step = 0; this.bar++; }
      }
    }

    chordAt(bar, step) {
      const chords = this.bars[bar % this.bars.length];
      return chords.length > 1 && step >= 4 ? chords[1] : chords[0];
    }

    play(bar, step, t) {
      const s = this.style, d = this.out, beat = 60 / s.bpm;
      const ch = this.chordAt(bar, step);
      const bars = this.bars[bar % this.bars.length];
      const changes = step === 0 || (step === 4 && bars.length > 1);
      const nextCh = step < 4 && bars.length > 1 ? bars[1] : this.bars[(bar + 1) % this.bars.length][0];

      // Bass
      const bassAt = (pc, dur) => {
        this.bassNote = nearest(pc, this.bassNote, 36, 50);
        INSTRUMENTS.bass(d, t, this.bassNote, dur, rand(0.85, 1));
      };
      if (s.bass === 'walk' && step % 2 === 0) {
        const lastBeat = step === 6 || (step === 2 && bars.length > 1);
        if (changes) bassAt(ch.root, beat * 0.9);
        else if (lastBeat) {
          this.bassNote = nearest(nextCh.root, this.bassNote, 36, 50) + pick([-1, 1]);
          INSTRUMENTS.bass(d, t, this.bassNote, beat * 0.9, 0.8);
        } else bassAt((ch.root + pick(ch.tones.slice(1, 3))) % 12, beat * 0.9);
      } else if (s.bass === 'two' && (step === 0 || step === 4)) {
        bassAt(changes ? ch.root : (ch.root + 7) % 12, beat * 1.8);
      } else if (s.bass === 'bossa') {
        if (step === 0) bassAt(ch.root, beat * 1.4);
        if (step === 3 || step === 4) bassAt((ch.root + 7) % 12, step === 3 ? beat * 0.45 : beat * 1.4);
        if (step === 7) bassAt(nextCh.root, beat * 0.45);
      }

      // Chords
      const play = INSTRUMENTS[s.compWith];
      const hit = (dur, vel) => voicing(ch).forEach(m => play(d, t + rand(0, 0.012), m, dur, vel));
      if (s.comp === 'swing') {
        if (step === 0) this.pattern = pick(SWING_COMP);
        if (this.pattern.includes(step)) hit(rand(0.18, 0.4), 0.55);
      } else if (s.comp === 'pads' && changes) {
        hit(beat * (bars.length > 1 ? 1.8 : 3.6), 0.5);
      } else if (s.comp === 'stride' && (step === 2 || step === 6)) {
        hit(0.2, 0.6);
      } else if (s.comp === 'bossa' && (bar % 2 === 0 ? [0, 3, 6] : [2, 5]).includes(step)) {
        hit(0.3, 0.55);
      } else if (s.comp === 'arp' && step % 2 === 0) {
        const notes = voicing(ch, true);
        notes.push(notes[0] + 12);
        play(d, t, notes[(step / 2) % notes.length], beat * 1.2, 0.42);
      }

      // Drums
      const D = DRUMS;
      if (s.drums === 'swing') {
        const ride = { 0: 0.7, 2: 0.95, 3: 0.5, 4: 0.7, 6: 0.95, 7: 0.5 }[step];
        if (ride) D.ride(d, t, ride);
        if (step === 2 || step === 6) { D.chick(d, t, 1); D.brush(d, t, 0.5); }
        if (step % 2 === 0) D.kick(d, t, 0.12);
      } else if (s.drums === 'brushes') {
        if (step % 2 === 0) D.brush(d, t, step === 2 || step === 6 ? 0.9 : 0.5);
        if (step === 2 || step === 6) D.chick(d, t, 0.6);
      } else if (s.drums === 'hot') {
        if (step === 2 || step === 6) { D.brush(d, t, 1); D.chick(d, t, 0.8); }
        if (step === 0 || step === 4) D.kick(d, t, 0.45);
        if (step % 2 === 1) D.ride(d, t, 0.35);
      } else if (s.drums === 'bossa') {
        if ((bar % 2 === 0 ? [0, 3, 6] : [2, 4]).includes(step)) D.rim(d, t, 0.9);
        D.shaker(d, t, step % 2 === 0 ? 1 : 0.6);
        if (step === 0 || step === 4) D.kick(d, t, 0.35);
      } else if (s.drums === 'block' && step === 0 && bar % 2 === 0) {
        D.block(d, t, 0.8);
      }

      // Melody: phrases of a bar or two, then space.
      if (step === 0 && bar % 4 === 0) this.phrase = pick([[0, 1], [1, 2], [0, 1, 2], [0, 2]]);
      const inPhrase = this.phrase && this.phrase.includes(bar % 4);
      const chance = s.density * (step % 2 === 0 ? 1.1 : 0.7);
      if (inPhrase && t >= this.leadUntil && Math.random() < chance) {
        const scale = s.pentatonic || ch.scale.map(i => (ch.root + i) % 12);
        const chordPcs = ch.tones.map(i => (ch.root + i) % 12);
        let options = [];
        for (let m = s.range[0]; m <= s.range[1]; m++) {
          const pc = m % 12, step2 = Math.abs(m - this.leadNote);
          if (scale.includes(pc) && step2 > 0 && step2 <= 5) options.push(m);
        }
        if ((step === 0 || step === 4) && !s.pentatonic) {
          const strong = options.filter(m => chordPcs.includes(m % 12));
          if (strong.length) options = strong;
        }
        if (options.length) {
          options.sort((a, b) => Math.abs(a - this.leadNote) - Math.abs(b - this.leadNote));
          this.leadNote = options[Math.floor(Math.pow(Math.random(), 1.6) * options.length)];
          const steps = pick([1, 2, 2, 3, 4]);
          const dur = steps * (60 / s.bpm / 2) * 0.95;
          INSTRUMENTS[s.lead](d, t, this.leadNote, dur, rand(0.55, 0.8));
          this.leadUntil = t + dur * 0.85;
        }
      }
    }

    stop() {
      clearInterval(this.timer);
      const t = ctx.currentTime;
      this.out.gain.cancelScheduledValues(t);
      this.out.gain.setValueAtTime(Math.max(this.out.gain.value, 0.0001), t);
      this.out.gain.exponentialRampToValueAtTime(0.0001, t + 1.2);
      const out = this.out;
      setTimeout(() => out.disconnect(), 1600);
    }
  }

  function updateMusic() {
    const want = prefs.music && running() ? wantedRoom : null;
    if (band && band.room === want) return;
    if (band) { band.stop(); band = null; }
    if (want) band = new Band(want);
  }

  // ---------- Sound effects ----------

  function tone(t, type, freq, peak, decay, wet) {
    const g = gain(0), end = shape(g, t, 0.003, peak, 0.0001, 0.006, decay);
    osc(type, freq, t, end).connect(g);
    g.connect(sfxBus);
    if (wet) { const s = gain(wet); g.connect(s); s.connect(reverbIn); }
  }
  function bell(t, midi, peak, decay) {
    const f = mtof(midi), g = gain(0), end = shape(g, t, 0.003, peak, 0.0001, 0.01, decay);
    osc('sine', f, t, end).connect(g);
    const h = gain(0.15);
    osc('sine', f * 4, t, end).connect(h);
    h.connect(g);
    g.connect(sfxBus);
  }
  function hiss(t, type, freq, q, peak, decay, sweepTo) {
    const g = gain(0), f = filter(type, freq, q);
    const end = shape(g, t, 0.002, peak, 0.0001, 0.004, decay);
    if (sweepTo) f.frequency.exponentialRampToValueAtTime(sweepTo, t + decay);
    const n = noise(t, end);
    n.connect(f); f.connect(g); g.connect(sfxBus);
  }

  const EFFECTS = {
    flick(t, v = 1) { hiss(t, 'bandpass', 3800, 1.2, 0.22 * v, 0.07); },
    select(t) { hiss(t, 'bandpass', 4600, 1.5, 0.08, 0.04); },
    deal(t) { for (let i = 0; i < 13; i++) EFFECTS.flick(t + i * 0.06, 0.8); },
    play(t, cards = 1) {
      const v = Math.min(1, 0.65 + cards * 0.08);
      hiss(t, 'lowpass', 1500, 0.7, 0.5 * v, 0.13);
      const g = gain(0), end = shape(g, t, 0.002, 0.4 * v, 0.0001, 0.005, 0.13);
      const o = osc('sine', 170, t, end);
      o.frequency.exponentialRampToValueAtTime(65, t + 0.09);
      o.connect(g); g.connect(sfxBus);
      EFFECTS.flick(t, 0.5);
    },
    pass(t) {
      tone(t, 'sine', 540, 0.16, 0.09);
      tone(t, 'sine', 810, 0.07, 0.06);
    },
    turn(t) {
      bell(t, 84, 0.1, 0.9);
      bell(t + 0.12, 91, 0.08, 1.1);
    },
    sweep(t) { hiss(t, 'bandpass', 500, 1.4, 0.16, 0.38, 3600); },
    tick(t) { tone(t, 'sine', 1650, 0.1, 0.03); },
    error(t) {
      tone(t, 'triangle', 165, 0.16, 0.16);
      tone(t + 0.11, 'triangle', 140, 0.14, 0.2);
    },
    join(t) {
      bell(t, 79, 0.09, 0.6);
      bell(t + 0.1, 84, 0.09, 0.8);
    },
    win(t) { [72, 76, 79, 83, 86].forEach((m, i) => bell(t + i * 0.07, m, 0.1, 1.2)); },
    lose(t) {
      bell(t, 67, 0.08, 0.7);
      bell(t + 0.16, 63, 0.07, 0.9);
    },
    fanfare(t) {
      [72, 76, 79, 84, 88].forEach((m, i) => bell(t + i * 0.09, m, 0.11, 1.4));
      [72, 76, 79, 83].forEach(m => bell(t + 0.55, m, 0.06, 2));
    },
    click(t) { tone(t, 'sine', 1200, 0.05, 0.02); },
    pop(t) {
      const g = gain(0), end = shape(g, t, 0.003, 0.14, 0.0001, 0.005, 0.12);
      const o = osc('sine', 420, t, end);
      o.frequency.exponentialRampToValueAtTime(880, t + 0.07);
      o.connect(g); g.connect(sfxBus);
    }
  };

  // ---------- Public API ----------

  window.Sound = {
    STYLES,

    /** Call from a tap or key press: browsers only start audio after the player interacts. */
    unlock() {
      if (!init()) return;
      unlocked = true;
      if (ctx.state !== 'running') ctx.resume().then(updateMusic, () => { /* still blocked */ });
      else updateMusic();
    },

    /** The room whose band should play, or null for silence. */
    music(roomId) {
      wantedRoom = roomId || null;
      updateMusic();
    },

    /** Plays a named effect (see EFFECTS), e.g. Sound.effect('play', 5). */
    effect(name, arg) {
      if (!running() || !prefs.sfx || !EFFECTS[name]) return;
      EFFECTS[name](ctx.currentTime + 0.01, arg);
    },

    prefs() {
      return Object.assign({}, prefs);
    },

    /** Changes and saves settings, e.g. Sound.set({ music: false }). */
    set(changes) {
      Object.assign(prefs, changes);
      savePrefs();
      applyVolumes(false);
      updateMusic();
    },

    title(roomId) {
      return (STYLES[roomId] || STYLES['gold-room']).title;
    }
  };

  // Pause everything while the page is in the background (saves battery on phones).
  document.addEventListener('visibilitychange', () => {
    if (!ctx || !unlocked) return;
    if (document.hidden) ctx.suspend();
    else ctx.resume().then(updateMusic, () => { /* needs another tap */ });
  });
})();
