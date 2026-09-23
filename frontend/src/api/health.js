// These go through nginx's /health-proxy/* routes (see nginx.conf), which
// server-side-proxy to each e-commerce service's real /actuator/health -
// avoiding a browser-side CORS failure that direct fetches to those services'
// own ports would hit (Spring Actuator does not send permissive CORS headers
// by default, and none of the ecommerce services were configured to add them).
const SERVICES = [
  { key: 'order', label: 'Order Service', path: '/health-proxy/order/' },
  { key: 'payment', label: 'Payment Service', path: '/health-proxy/payment/' },
  { key: 'inventory', label: 'Inventory Service', path: '/health-proxy/inventory/' },
  { key: 'catalog', label: 'Product Catalog Service', path: '/health-proxy/catalog/' },
];
export async function checkAllHealth() {
  return Promise.all(
    SERVICES.map(async (svc) => {
      try {
        const res = await fetch(svc.path, { method: 'GET' });
        if (!res.ok) return { ...svc, status: 'DOWN', detail: `HTTP ${res.status}` };
        const body = await res.json();
        return { ...svc, status: body.status || 'UNKNOWN', detail: null };
      } catch (e) {
        return { ...svc, status: 'UNREACHABLE', detail: e.message };
      }
    })
  );
}
