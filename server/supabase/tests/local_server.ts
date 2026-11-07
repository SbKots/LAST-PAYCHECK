// Docker-free local harness: real PostgREST + production handler; Auth/provider fixtures only.
// Never deploy this file to hosted Supabase.
import { backend } from "../functions/_shared/backend.ts";
import { createHandler } from "../functions/providers/index.ts";
import { createCatalogHandler } from "../functions/catalog/index.ts";
const port = Number(Deno.env.get("TEST_API_PORT") ?? 18121);
const restPort = Number(Deno.env.get("TEST_REST_PORT") ?? 18120);
const secret = Deno.env.get("TEST_JWT_SECRET")!;
const serviceKey = Deno.env.get("TEST_SERVICE_JWT")!;
const users = new Set((Deno.env.get("TEST_USER_IDS") ?? "").split(","));
function bytes(part: string): Uint8Array<ArrayBuffer> {
  const s = atob(part.replaceAll("-", "+").replaceAll("_", "/"));
  return Uint8Array.from(s, (c) => c.charCodeAt(0));
}
async function session(request: Request) {
  const token = request.headers.get("Authorization")?.replace(/^Bearer /i, "") ?? "";
  try {
    const [header, payload, signature, ...extra] = token.split(".");
    if (extra.length || JSON.parse(new TextDecoder().decode(bytes(header))).alg !== "HS256") {
      throw new Error();
    }
    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"],
    );
    if (
      !await crypto.subtle.verify(
        "HMAC",
        key,
        bytes(signature),
        new TextEncoder().encode(`${header}.${payload}`),
      )
    ) throw new Error();
    const claims = JSON.parse(new TextDecoder().decode(bytes(payload)));
    if (
      claims.exp <= Date.now() / 1000 || claims.role !== "authenticated" || !users.has(claims.sub)
    ) throw new Error();
    return Response.json({
      id: claims.sub,
      is_anonymous: claims.is_anonymous === true,
      user_metadata: claims.user_metadata ?? {},
    });
  } catch {
    return Response.json({ error: "invalid session" }, { status: 401 });
  }
}
const fixtureFetch: typeof fetch = (input) => {
  const u = new URL(String(input));
  if (u.hostname === "api-v2.onetcenter.org") {
    return Promise.resolve(Response.json(
      u.pathname.endsWith("skills")
        ? { element: [{ id: "2.A.1.a", name: "Reading" }], total: 1 }
        : { career: [{ code: "53-7062.00", title: "Warehouse" }], total: 1 },
    ));
  }
  if (u.hostname === "api.cars-base.ru") {
    return Promise.resolve(
      Response.json({
        data: [{ id: "demo", name: "Demo" }],
        meta: { count: 1, page: Number(u.searchParams.get("page")) },
      }),
    );
  }
  if (u.hostname === "api.worldbank.org") {
    return Promise.resolve(
      Response.json([
        { pages: 1 },
        [2020, 2021, 2022].map((year) => ({
          countryiso3code: "RUS",
          indicator: { id: "FP.CPI.TOTL.ZG" },
          date: String(year),
          value: 4,
        })),
      ]),
    );
  }
  return Promise.reject(new Error("Unexpected upstream in local fixture"));
};
const deps = backend({
  url: `http://127.0.0.1:${port}`,
  publicKey: "local-public-fixture",
  serviceKey,
});
const handler = createHandler({ ...deps, fetcher: fixtureFetch, sleep: () => Promise.resolve() }, {
  onet: "local-onet-fixture",
  cars: "test",
});
const catalog = createCatalogHandler({
  url: `http://127.0.0.1:${port}`,
  publicKey: Deno.env.get("TEST_ANON_JWT"),
});
Deno.serve({ hostname: "127.0.0.1", port }, async (request) => {
  const u = new URL(request.url);
  if (u.pathname === "/health") return Response.json({ local: true });
  if (u.pathname === "/auth/v1/user") return await session(request);
  if (u.pathname.startsWith("/functions/v1/catalog")) return await catalog(request);
  if (u.pathname.startsWith("/rest/v1/")) {
    u.port = String(restPort);
    u.pathname = u.pathname.replace("/rest/v1", "");
    return await fetch(new Request(u, request));
  }
  return await handler(request);
});
