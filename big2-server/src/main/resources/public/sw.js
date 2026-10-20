// Service worker: makes Big 2 installable and quick to open.
// Game files are fetched from the network first (so updates show up straight away) and fall back
// to the saved copy when offline. The game itself needs the server, so nothing here touches /ws.
const CACHE = 'big2-v3';
const SHELL = [
  '/',
  '/index.html',
  '/css/app.css',
  '/js/cards.js',
  '/js/rooms.js',
  '/js/combos.js',
  '/js/sound.js',
  '/js/app.js',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/apple-touch-icon.png',
  '/icons/favicon-32.png'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // Fonts rarely change: serve the saved copy and refresh it in the background.
  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    event.respondWith(caches.open(CACHE).then(async cache => {
      const saved = await cache.match(req);
      const fresh = fetch(req).then(res => { cache.put(req, res.clone()); return res; }).catch(() => saved);
      return saved || fresh;
    }));
    return;
  }

  if (url.origin !== location.origin || url.pathname === '/ws' || url.pathname === '/health') return;

  // Our own files: newest from the network, saved copy when offline.
  event.respondWith(
    fetch(req)
      .then(res => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then(cache => cache.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req).then(saved => saved || caches.match('/index.html')))
  );
});
