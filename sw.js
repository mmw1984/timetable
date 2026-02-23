// Service Worker for Timetable PWA
const CACHE_NAME = 'timetable-v1';
const ASSETS_TO_CACHE = [
  '/',
  '/index.html',
  '/timetable-data.js',
  '/manifest.json'
];

// Notification queue to store scheduled notifications
let notificationQueue = [];

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

// Fetch event - serve from cache first, fallback to network
self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request)
      .then((response) => {
        // Cache hit - return response
        if (response) {
          console.log('[SW] Serving from cache:', event.request.url);

          // Update cache in background for next time
          fetch(event.request).then((freshResponse) => {
            if (freshResponse && freshResponse.status === 200) {
              caches.open(CACHE_NAME).then((cache) => {
                cache.put(event.request, freshResponse);
              });
            }
          }).catch(() => {
            // Network failed, but we have cached version
          });

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
        }).catch(() => {
          // Return offline page or error
          return new Response('Offline - Please check your connection', {
            status: 503,
            statusText: 'Service Unavailable',
            headers: new Headers({
              'Content-Type': 'text/plain'
            })
          });
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
    clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        // If app is already open, focus it
        for (const client of clientList) {
          if (client.url.includes(self.location.origin) && 'focus' in client) {
            return client.focus();
          }
        }
        // Otherwise open a new window
        if (clients.openWindow) {
          return clients.openWindow('/');
        }
      })
  );
});

// Handle messages from the client
self.addEventListener('message', (event) => {
  console.log('[SW] Message received:', event.data);

  if (event.data && event.data.type === 'SCHEDULE_NOTIFICATION') {
    const { title, body, timestamp, id } = event.data;
    const delay = timestamp - Date.now();

    if (delay > 0) {
      // Store in queue
      const timeoutId = setTimeout(() => {
        self.registration.showNotification(title, {
          body: body,
          icon: '/manifest.json',
          badge: '/manifest.json',
          vibrate: [200, 100, 200],
          tag: `timetable-${id || Date.now()}`,
          requireInteraction: false,
          data: {
            url: '/',
            timestamp: timestamp
          }
        });

        // Remove from queue
        notificationQueue = notificationQueue.filter(n => n.id !== id);
      }, delay);

      notificationQueue.push({
        id: id || Date.now(),
        title,
        body,
        timestamp,
        timeoutId
      });

      console.log(`[SW] Scheduled notification for ${new Date(timestamp).toLocaleString()}`);
    }
  }

  if (event.data && event.data.type === 'CANCEL_NOTIFICATIONS') {
    // Clear all scheduled notifications
    notificationQueue.forEach(notification => {
      clearTimeout(notification.timeoutId);
    });
    notificationQueue = [];
    console.log('[SW] Cancelled all scheduled notifications');
  }

  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }

  if (event.data && event.data.type === 'GET_NOTIFICATION_QUEUE') {
    // Send back the notification queue
    event.ports[0].postMessage({
      type: 'NOTIFICATION_QUEUE',
      queue: notificationQueue.map(n => ({
        id: n.id,
        title: n.title,
        body: n.body,
        timestamp: n.timestamp
      }))
    });
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

// Background sync for offline actions (if supported)
self.addEventListener('sync', (event) => {
  if (event.tag === 'sync-notifications') {
    event.waitUntil(syncNotifications());
  }
});

async function syncNotifications() {
  console.log('[SW] Syncing notifications...');
  // This can be used to sync any pending notification schedules
  // when the device comes back online
}

