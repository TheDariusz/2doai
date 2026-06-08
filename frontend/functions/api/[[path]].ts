/// <reference types="@cloudflare/workers-types" />

// Pattern B reverse proxy: forward every /api/* request to the Fly backend,
// preserving the full path (so /api/v1/... reaches the backend unchanged) and
// giving the browser a single same-origin domain (no CORS). BACKEND_ORIGIN is a
// non-secret Pages var, e.g. https://2doai.fly.dev — see frontend/wrangler.toml.
export const onRequest: PagesFunction<{ BACKEND_ORIGIN: string }> = async ({ request, env }) => {
  const url = new URL(request.url)
  const target = new URL(env.BACKEND_ORIGIN)
  url.protocol = target.protocol
  url.host = target.host
  url.port = target.port
  return fetch(new Request(url, request))
}
