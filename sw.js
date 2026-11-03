// Service Worker: hält die App offline verfügbar (die Spracherkennung selbst braucht weiterhin Internet).
const CACHE = 'mathe-mitschrift-v2';
const SHELL = ['./', './index.html', './manifest.webmanifest', './icon.svg', './icon-192.png', './icon-512.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).hostname === 'api.anthropic.com') return;
  // Netz zuerst (immer neueste Version), bei Offline aus dem Cache
  e.respondWith(fetch(req).then(res => {
    if (res.ok && (res.type === 'basic' || res.type === 'cors')){ const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
    return res;
  }).catch(() => caches.match(req).then(r => r || caches.match('./index.html'))));
});
