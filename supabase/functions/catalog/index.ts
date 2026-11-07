import { boundedText, object, Problem, problemResponse } from "../_shared/http.ts";

export type CatalogConfig = { url?: string; publicKey?: string };
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Max-Age": "600",
};

function finish(response: Response): Response {
  for (const [name, value] of Object.entries(cors)) response.headers.set(name, value);
  response.headers.set("X-Content-Type-Options", "nosniff");
  if (response.status !== 204) {
    response.headers.set("Content-Type", "application/json; charset=utf-8");
  }
  return response;
}

function unavailable(): Response {
  return finish(
    problemResponse(new Problem(503, "UNAVAILABLE", "Catalog temporarily unavailable")),
  );
}

function publicHeaders(key: string): Headers {
  const headers = new Headers({
    apikey: key,
    "Content-Type": "application/json",
    "Content-Profile": "lastpay_api",
    "Accept-Profile": "lastpay_api",
  });
  if (/^sb_publishable_[A-Za-z0-9_-]+$/.test(key)) return headers;

  // This only rejects a misconfigured privileged key. Data API verifies the JWT itself.
  const parts = key.split(".");
  if (parts.length !== 3 || parts.some((part) => !/^[A-Za-z0-9_-]+$/.test(part))) {
    throw new Error("Public key required");
  }
  const payload = parts[1].replaceAll("-", "+").replaceAll("_", "/");
  if (object(JSON.parse(atob(payload))).role !== "anon") throw new Error("Anon key required");
  headers.set("Authorization", `Bearer ${key}`);
  return headers;
}

export function createCatalogHandler(config: CatalogConfig, fetcher: typeof fetch = fetch) {
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") {
      return finish(new Response(null, { status: 204, headers: { Allow: "GET, OPTIONS" } }));
    }
    if (request.method !== "GET") {
      return finish(problemResponse(new Problem(405, "METHODNOTALLOWED", "GET required")));
    }
    const url = new URL(request.url);
    if (!["/catalog", "/functions/v1/catalog"].includes(url.pathname.replace(/\/$/, ""))) {
      return finish(problemResponse(new Problem(404, "NOTFOUND", "Route not found")));
    }
    // No version, resource, SQL arguments or private-schema selection from the browser.
    if (url.search) {
      return finish(
        problemResponse(new Problem(400, "BADREQUEST", "Query parameters not supported")),
      );
    }
    try {
      if (!config.url || !config.publicKey) return unavailable();
      const base = new URL(config.url);
      const local = ["127.0.0.1", "localhost", "[::1]"].includes(base.hostname);
      if (
        (base.protocol !== "https:" && !(local && base.protocol === "http:")) ||
        base.username || base.password || base.search || base.hash || base.pathname !== "/"
      ) return unavailable();
      const response = await fetcher(new URL("/rest/v1/rpc/get_catalog", base), {
        method: "POST",
        headers: publicHeaders(config.publicKey),
        body: JSON.stringify({ args: {} }),
        signal: AbortSignal.timeout(5000),
        redirect: "error",
      });
      if (response.status !== 200) {
        await response.body?.cancel();
        return response.status === 429
          ? finish(problemResponse(new Problem(429, "RATELIMITED", "Too many catalog requests")))
          : unavailable();
      }
      const value = object(JSON.parse(await boundedText(response, 1024 * 1024)));
      if (typeof value.catalogVersion !== "string" || !Array.isArray(value.resources)) {
        return unavailable();
      }
      return finish(
        new Response(JSON.stringify(value, null, 2), {
          headers: { "Cache-Control": "no-store" },
        }),
      );
    } catch {
      // Never serialize upstream errors, bodies, URLs, credentials or request headers.
      return unavailable();
    }
  };
}

if (import.meta.main) {
  Deno.serve(createCatalogHandler({
    url: Deno.env.get("SUPABASE_URL"),
    publicKey: Deno.env.get("SB_PUBLISHABLE_KEY") ?? Deno.env.get("SUPABASE_ANON_KEY"),
  }));
}
