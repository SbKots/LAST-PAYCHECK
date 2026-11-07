import { createCatalogHandler } from "../functions/catalog/index.ts";

function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error("Values differ");
}
const config = { url: "https://project.supabase.co", publicKey: "sb_publishable_fixture" };
const manifest = {
  catalogVersion: "demo-1",
  schemaVersion: "1",
  publishedAt: "2026-11-06T00:00:00Z",
  contentSha256: "a".repeat(64),
  resources: ["occupations", "skills"],
  sources: [{ resource: "skills", source: "DEMO" }],
};
const request = (suffix = "", method = "GET", headers?: HeadersInit) =>
  new Request(`https://project.supabase.co/functions/v1/catalog${suffix}`, { method, headers });
function legacyKey(role: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replaceAll("=", "");
  return `${encode({ alg: "HS256" })}.${encode({ role })}.fixture`;
}
const noFetch: typeof fetch = () => Promise.reject(new Error("Unexpected database request"));

Deno.test("catalog: browser GET returns exact published RPC JSON without caller credentials", async () => {
  let calls = 0;
  const handler = createCatalogHandler(config, (input, init) => {
    calls++;
    equal(String(input), "https://project.supabase.co/rest/v1/rpc/get_catalog");
    equal(init?.method, "POST");
    equal(JSON.parse(String(init?.body)), { args: {} });
    const headers = new Headers(init?.headers);
    equal(headers.get("apikey"), config.publicKey);
    equal(headers.get("Authorization"), null);
    equal(headers.get("Content-Profile"), "lastpay_api");
    equal(headers.get("Accept-Profile"), "lastpay_api");
    equal(init?.redirect, "error");
    equal(init?.signal instanceof AbortSignal, true);
    return Promise.resolve(Response.json(manifest));
  });
  for (
    const headers of [undefined, {
      Authorization: `Bearer ${legacyKey("service_role")}`,
      apikey: "caller-key",
      "Content-Profile": "lastpay_private",
      "Accept-Profile": "lastpay_private",
    }]
  ) {
    const response = await handler(request("", "GET", headers));
    equal(response.status, 200);
    equal(response.headers.get("Content-Type"), "application/json; charset=utf-8");
    equal(response.headers.get("Access-Control-Allow-Origin"), "*");
    equal(response.headers.get("Cache-Control"), "no-store");
    equal(response.headers.get("X-Content-Type-Options"), "nosniff");
    equal(await response.json(), manifest);
  }
  equal(calls, 2);
});

Deno.test("catalog: legacy anon key never uses caller JWT", async () => {
  const key = legacyKey("anon");
  const handler = createCatalogHandler({ ...config, publicKey: key }, (_input, init) => {
    const headers = new Headers(init?.headers);
    equal(headers.get("Authorization"), `Bearer ${key}`);
    equal(headers.get("apikey"), key);
    return Promise.resolve(Response.json(manifest));
  });
  const response = await handler(request("", "GET", { Authorization: "Bearer caller-token" }));
  equal(response.status, 200);
  await response.body?.cancel();
});

Deno.test("catalog: OPTIONS requires no configuration and never queries the database", async () => {
  const response = await createCatalogHandler({}, noFetch)(request("", "OPTIONS"));
  equal(response.status, 204);
  equal(await response.text(), "");
  equal(response.headers.get("Access-Control-Allow-Methods"), "GET, OPTIONS");
  equal(response.headers.get("Access-Control-Allow-Origin"), "*");
  equal(response.headers.get("Access-Control-Allow-Credentials"), null);
});

for (const method of ["POST", "PUT", "PATCH", "DELETE", "HEAD"]) {
  Deno.test(`catalog: ${method} rejected without any database request`, async () => {
    const response = await createCatalogHandler(config, noFetch)(request("", method));
    equal(response.status, 405);
    equal(response.headers.get("Allow"), "GET, OPTIONS");
    equal((await response.json()).error.code, "METHODNOTALLOWED");
  });
}
for (const suffix of ["?catalogVersion=draft", "?resource=user_save", "?schema=lastpay_private"]) {
  Deno.test(`catalog: selection parameters rejected ${suffix}`, async () => {
    const response = await createCatalogHandler(config, noFetch)(request(suffix));
    equal(response.status, 400);
    equal((await response.json()).error.code, "BADREQUEST");
  });
}
Deno.test("catalog: unknown subroute is not a database proxy", async () => {
  const response = await createCatalogHandler(config, noFetch)(request("/provider_cache"));
  equal(response.status, 404);
  await response.body?.cancel();
});
Deno.test("catalog: runtime mount path and trailing slash supported", async () => {
  const handler = createCatalogHandler(config, () => Promise.resolve(Response.json(manifest)));
  for (const url of ["https://edge.test/catalog", "https://edge.test/functions/v1/catalog/"]) {
    const response = await handler(new Request(url));
    equal(response.status, 200);
    await response.body?.cancel();
  }
});
for (
  const bad of [
    {},
    { ...config, publicKey: "sb_secret_fixture" },
    { ...config, publicKey: legacyKey("service_role") },
    { ...config, publicKey: legacyKey("authenticated") },
    { ...config, publicKey: "invalid" },
    { ...config, url: "http://remote.test" },
    { ...config, url: "https://user:password@remote.test" },
  ]
) {
  Deno.test(`catalog: unsafe or missing configuration ${Object.keys(bad).join(",")}`, async () => {
    let calls = 0;
    const response = await createCatalogHandler(bad, () => {
      calls++;
      throw new Error("Unexpected request");
    })(request());
    equal(calls, 0);
    equal(response.status, 503);
    equal(response.headers.get("Retry-After"), "60");
    equal((await response.json()).error.message, "Catalog temporarily unavailable");
  });
}
for (const status of [401, 403, 404, 500, 503, 302]) {
  Deno.test(`catalog: upstream ${status} becomes safe 503`, async () => {
    const response = await createCatalogHandler(
      config,
      () => Promise.resolve(new Response("private upstream details", { status })),
    )(request());
    equal(response.status, 503);
    equal(response.headers.get("Content-Type"), "application/json; charset=utf-8");
    equal(response.headers.get("Access-Control-Allow-Origin"), "*");
    equal((await response.json()).error.message, "Catalog temporarily unavailable");
  });
}
Deno.test("catalog: rate limit preserved with safe JSON", async () => {
  const response = await createCatalogHandler(
    config,
    () => Promise.resolve(new Response("", { status: 429 })),
  )(request());
  equal(response.status, 429);
  equal(response.headers.get("Retry-After"), "60");
  equal((await response.json()).error.code, "RATELIMITED");
});
for (const body of ["<html>error</html>", "null", "[]", '{"private":"details"}']) {
  Deno.test(`catalog: malformed RPC body returns 503 ${body[0]}`, async () => {
    const response = await createCatalogHandler(config, () => Promise.resolve(new Response(body)))(
      request(),
    );
    equal(response.status, 503);
    await response.body?.cancel();
  });
}
Deno.test("catalog: oversized response rejected and not returned", async () => {
  const response = await createCatalogHandler(
    config,
    () => Promise.resolve(new Response("{}", { headers: { "Content-Length": "1048577" } })),
  )(request());
  equal(response.status, 503);
  await response.body?.cancel();
});
for (const name of ["AbortError", "TimeoutError", "TypeError"]) {
  Deno.test(`catalog: ${name} produces 503 without leaking the exception`, async () => {
    const response = await createCatalogHandler(
      config,
      () => Promise.reject(new DOMException("private credentials and endpoint", name)),
    )(request());
    equal(response.status, 503);
    equal((await response.json()).error.message, "Catalog temporarily unavailable");
  });
}
