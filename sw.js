// Service Worker for 實時時間表 PWA
const CACHE_NAME = 'timetable-v1.0.0';
const STATIC_CACHE = 'timetable-static-v1.0.0';
const DYNAMIC_CACHE = 'timetable-dynamic-v1.0.0';

// Files to cache immediately on install
const STATIC_ASSETS = [
  './',
  './index.html',
  './style.css',
  './script.js',
  './timetable-data.js',
  './api.js',
  './notifications.js',
  './manifest.json',
  'https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,1,0',
  'https://fonts.googleapis.com/css2?family=Noto+Sans+TC:wght@400;500;600;700&display=swap'
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
  console.log('[SW] Installing service worker...');
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then((cache) => {
        console.log('[SW] Caching static assets');
        return cache.addAll(STATIC_ASSETS);
      })
      .then(() => self.skipWaiting())
      .catch((err) => console.error('[SW] Failed to cache:', err))
  );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating service worker...');
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => {
        return Promise.all(
          cacheNames
            .filter((name) => name !== STATIC_CACHE && name !== DYNAMIC_CACHE)
            .map((name) => {
              console.log('[SW] Deleting old cache:', name);
              return caches.delete(name);
            })
        );
      })
      .then(() => self.clients.claim())
  );
});

// Fetch event - serve from cache, fallback to network
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-GET requests
  if (request.method !== 'GET') return;

  // Skip chrome-extension and other non-http(s) requests
  if (!url.protocol.startsWith('http')) return;

  event.respondWith(
    caches.match(request)
      .then((cachedResponse) => {
        if (cachedResponse) {
          // Return cached response and update cache in background
          event.waitUntil(updateCache(request));
          return cachedResponse;
        }

        // Not in cache, fetch from network
        return fetch(request)
          .then((networkResponse) => {
            // Clone the response before caching
            const responseToCache = networkResponse.clone();
            
            // Cache the new response
            caches.open(DYNAMIC_CACHE)
              .then((cache) => {
                cache.put(request, responseToCache);
              });

            return networkResponse;
          })
          .catch(() => {
            // Network failed, return offline fallback for HTML pages
            if (request.headers.get('accept')?.includes('text/html')) {
              return caches.match('./index.html');
            }
            return new Response('Offline', { status: 503 });
          });
      })
  );
});

// Update cache in background (stale-while-revalidate)
async function updateCache(request) {
  try {
    const networkResponse = await fetch(request);
    const cache = await caches.open(DYNAMIC_CACHE);
    await cache.put(request, networkResponse.clone());
  } catch (err) {
    // Network request failed, ignore
  }
}

// Handle push notifications
self.addEventListener('push', (event) => {
  console.log('[SW] Push received');
  
  let data = {
    title: '時間表提醒',
    body: '您有新的通知',
    icon: './icons/icon-192.png',
    badge: './icons/icon-72.png',
    tag: 'timetable-notification',
    requireInteraction: false,
    actions: [
      { action: 'view', title: '查看' },
      { action: 'dismiss', title: '關閉' }
    ]
  };

  if (event.data) {
    try {
      data = { ...data, ...event.data.json() };
    } catch (e) {
      data.body = event.data.text();
    }
  }

  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: data.icon,
      badge: data.badge,
      tag: data.tag,
      requireInteraction: data.requireInteraction,
      actions: data.actions,
      vibrate: [200, 100, 200],
      data: data
    })
  );
});

// Handle notification click
self.addEventListener('notificationclick', (event) => {
  console.log('[SW] Notification clicked');
  event.notification.close();

  if (event.action === 'dismiss') {
    return;
  }

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        // If app is already open, focus it
        for (const client of clientList) {
          if (client.url.includes('index.html') && 'focus' in client) {
            return client.focus();
          }
        }
        // Otherwise open a new window
        if (clients.openWindow) {
          return clients.openWindow('./index.html');
        }
      })
  );
});

// Handle background sync for notifications
self.addEventListener('sync', (event) => {
  console.log('[SW] Background sync:', event.tag);
  
  if (event.tag === 'sync-notifications') {
    event.waitUntil(syncNotifications());
  }
});

async function syncNotifications() {
  // This would sync notification preferences when back online
  console.log('[SW] Syncing notifications...');
}

// Periodic background sync for checking schedule
self.addEventListener('periodicsync', (event) => {
  if (event.tag === 'check-schedule') {
    event.waitUntil(checkScheduleAndNotify());
  }
});

async function checkScheduleAndNotify() {
  // Check if there's an upcoming class and notify
  console.log('[SW] Checking schedule for notifications...');
}
