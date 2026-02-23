// Service Worker for Timetable PWA
const CACHE_NAME = 'timetable-v1';
const ASSETS_TO_CACHE = [
  '/index.html',
  '/timetable-data.js',
  '/manifest.json'
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
  console.log('[SW] Installing service worker...');
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => {
        console.log('[SW] Caching app shell');
        return cache.addAll(ASSETS_TO_CACHE);
      })
      .then(() => self.skipWaiting())
  );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating service worker...');
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cacheName) => {
          if (cacheName !== CACHE_NAME) {
            console.log('[SW] Deleting old cache:', cacheName);
            return caches.delete(cacheName);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch event - serve from cache, fallback to network
self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request)
      .then((response) => {
        // Cache hit - return response
        if (response) {
          console.log('[SW] Serving from cache:', event.request.url);
          return response;
        }

        // Clone the request
        const fetchRequest = event.request.clone();

        return fetch(fetchRequest).then((response) => {
          // Check if valid response
          if (!response || response.status !== 200 || response.type !== 'basic') {
            return response;
          }

          // Clone the response
          const responseToCache = response.clone();

          caches.open(CACHE_NAME)
            .then((cache) => {
              cache.put(event.request, responseToCache);
            });

          return response;
        });
      })
  );
});

// Handle notification clicks
self.addEventListener('notificationclick', (event) => {
  console.log('[SW] Notification click received.');

  event.notification.close();

  // Open the app when notification is clicked
  event.waitUntil(
    clients.openWindow('/')
  );
});

// Handle messages from the client
self.addEventListener('message', (event) => {
  console.log('[SW] Message received:', event.data);

  if (event.data && event.data.type === 'SCHEDULE_NOTIFICATION') {
    const { title, body, timestamp } = event.data;
    const delay = timestamp - Date.now();

    if (delay > 0) {
      setTimeout(() => {
        self.registration.showNotification(title, {
          body: body,
          icon: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 192 192"%3E%3Crect fill="%23007aff" width="192" height="192" rx="48"/%3E%3Ctext x="50%25" y="50%25" dominant-baseline="middle" text-anchor="middle" font-size="120" fill="white" font-family="sans-serif"%3E📅%3C/text%3E%3C/svg%3E',
          badge: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96"%3E%3Ccircle fill="%23007aff" cx="48" cy="48" r="48"/%3E%3Ctext x="50%25" y="50%25" dominant-baseline="middle" text-anchor="middle" font-size="60" fill="white" font-family="sans-serif"%3E📅%3C/text%3E%3C/svg%3E',
          vibrate: [200, 100, 200],
          tag: 'timetable-notification',
          requireInteraction: false
        });
      }, delay);
    }
  }

  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

// Periodic sync for updating timetable (if supported)
self.addEventListener('periodicsync', (event) => {
  if (event.tag === 'update-timetable') {
    event.waitUntil(updateTimetableCache());
  }
});

async function updateTimetableCache() {
  try {
    const cache = await caches.open(CACHE_NAME);
    await cache.add('/timetable-data.js');
    console.log('[SW] Timetable cache updated');
  } catch (error) {
    console.error('[SW] Failed to update timetable cache:', error);
  }
}
