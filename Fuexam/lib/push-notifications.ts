import { apiFetch } from "@/lib/api/client";

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = window.atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

export async function registerServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!("serviceWorker" in navigator)) {
    return null;
  }
  return navigator.serviceWorker.register("/sw.js");
}

export async function getVapidPublicKey(): Promise<{ publicKey: string; enabled: boolean }> {
  return apiFetch<{ publicKey: string; enabled: boolean }>("/api/v1/push/vapid-public-key");
}

export async function subscribeToPush(): Promise<boolean> {
  if (!("Notification" in window) || !("PushManager" in window)) {
    return false;
  }

  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    return false;
  }

  const vapid = await getVapidPublicKey();
  if (!vapid.enabled || !vapid.publicKey) {
    return false;
  }

  const registration = await registerServiceWorker();
  if (!registration) {
    return false;
  }

  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(vapid.publicKey) as BufferSource,
  });

  const json = subscription.toJSON();
  if (!json.endpoint || !json.keys?.p256dh || !json.keys?.auth) {
    return false;
  }

  await apiFetch<{ subscribed: boolean }>("/api/v1/users/me/push-subscriptions", {
    method: "POST",
    body: JSON.stringify({
      endpoint: json.endpoint,
      p256dh: json.keys.p256dh,
      auth: json.keys.auth,
    }),
  });
  return true;
}

export async function unsubscribeFromPush(): Promise<void> {
  if (!("serviceWorker" in navigator)) {
    return;
  }
  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    return;
  }
  const endpoint = subscription.endpoint;
  await subscription.unsubscribe();
  const params = new URLSearchParams({ endpoint });
  await apiFetch<{ subscribed: boolean }>(
    `/api/v1/users/me/push-subscriptions?${params}`,
    { method: "DELETE" },
  );
}
