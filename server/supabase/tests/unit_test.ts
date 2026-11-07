import { backend } from "../functions/_shared/backend.ts";
import { cachedProvider, type Dependencies } from "../functions/_shared/cache.ts";
import { boundedText, Problem, ProviderError } from "../functions/_shared/http.ts";
import { fetchProvider, integer, providerSpec, validate } from "../functions/_shared/providers.ts";
import { createHandler } from "../functions/providers/index.ts";

// Offline regression fixtures mirror the shapes observed from real APIs.
// These are not hosted test results and contain no external credentials.
const liveFormatCases = [
  {
    path: "/onet/occupations",
    value: {
      start: 1,
      end: 1,
      total: 1,
      career: [{ code: "15-1243.01", title: "Data Warehousing Specialists", tags: {} }],
    },
  },
  {
    path: "/onet/skills/53-7062.00",
    value: {
      start: 1,
      end: 1,
      total: 1,
      element: [{ id: "2.B.1.b", name: "Coordination", importance: 72 }],
    },
  },
  {
    path: "/cars/brands",
    value: {
      data: [{ id: "212", name: "212", country: "Китай" }],
      meta: { total: 50, count: 1, pageSize: 1, page: 1, demoMode: true },
    },
  },
  {
    path: "/cars/models?brandId=BMW",
    value: {
      data: [{ id: "BMW_02", mark_id: "BMW", name: "02 (E10)" }],
      meta: { total: 67, count: 1, pageSize: 1, page: 1, demoMode: true },
    },
  },
];
function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}
function throws(fn: () => unknown, status: number) {
  try {
    fn();
  } catch (e) {
    equal((e as Problem).status, status);
    return;
  }
  throw new Error("Expected exception");
}
async function rejects(fn: () => Promise<unknown>, reason: string) {
  try {
    await fn();
  } catch (e) {
    equal((e as ProviderError).reason, reason);
    return;
  }
  throw new Error("Expected rejection");
}
const url = (s: string) => new URL(`https://example.test/providers${s}`);
const spec = () => providerSpec(url("/onet/occupations"), { onet: "fake-key" });
const payload = { career: [{ code: "53-7062.00", title: "Warehouse" }], total: 1 };
Deno.test("integer inclusive boundaries", () => {
  equal(integer("1", 20, 1, 100), 1);
  equal(integer("100", 20, 1, 100), 100);
  equal(integer(null, 20, 1, 100), 20);
});
for (const raw of ["", "1.0", "+1", " 1", "1e2"]) {
  Deno.test(`integer syntax ${JSON.stringify(raw)}`, () =>
    throws(() => integer(raw, 20, 1, 100), 400));
}
for (const raw of ["0", "101", "999999999999999999999999999999"]) {
  Deno.test(`integer range ${raw}`, () => throws(() => integer(raw, 20, 1, 100), 422));
}
for (
  const path of [
    "/worldbank/inflation",
    "/worldbank/inflation?country=RUS&country=RUS&yearFrom=2020&yearTo=2022",
    "/onet/occupations?extra=1",
  ]
) {
  Deno.test(`invalid query ${path}`, () =>
    throws(() => providerSpec(url(path), { onet: "fake-key" }), 400));
}
for (
  const path of [
    "/worldbank/inflation?country=WLD&yearFrom=2020&yearTo=2022",
    "/worldbank/inflation?country=RUS&yearFrom=2024&yearTo=2022",
    "/worldbank/inflation?country=RUS&yearFrom=2000&yearTo=2020",
    "/onet/skills/bad",
    "/cars/models?brandId=!",
    "/cars/brands?page=0",
  ]
) {
  Deno.test(`invalid parameters ${path}`, () =>
    throws(() => providerSpec(url(path), { onet: "fake-key", cars: "test" }), 422));
}
for (
  const path of ["/onet/occupations", "/onet/skills/53-7062.00", "/cars/brands", "/cars/models"]
) {
  Deno.test(`disabled ${path}`, () => throws(() => providerSpec(url(path), {}), 503));
}
Deno.test("unknown route", () => throws(() => providerSpec(url("/other"), {}), 404));
Deno.test("ONET key in header only", () => {
  const s = spec();
  equal(s.headers["X-API-Key"], "fake-key");
  equal(s.url.toString().includes("fake-key"), false);
});
for (
  const [status, reason] of [[401, "AUTH"], [403, "AUTH"], [404, "NO_DATA"], [429, "UNAVAILABLE"], [
    500,
    "UNAVAILABLE",
  ], [302, "UNAVAILABLE"]] as const
) {
  Deno.test(`provider status ${status}`, () =>
    rejects(
      () =>
        fetchProvider(
          spec(),
          (() => Promise.resolve(new Response("{}", { status }))) as typeof fetch,
        ),
      reason,
    ));
}
Deno.test("provider valid response", async () =>
  equal(
    await fetchProvider(spec(), (() => Promise.resolve(Response.json(payload))) as typeof fetch),
    payload,
  ));
Deno.test("provider invalid JSON", () =>
  rejects(
    () => fetchProvider(spec(), (() => Promise.resolve(new Response("{"))) as typeof fetch),
    "INVALID",
  ));
Deno.test("provider secret echo rejected", () =>
  rejects(
    () =>
      fetchProvider(
        spec(),
        (() =>
          Promise.resolve(
            Response.json({ career: [{ code: "a", title: "fake-key" }] }),
          )) as typeof fetch,
      ),
    "INVALID",
  ));
Deno.test("provider timeout", () =>
  rejects(
    () =>
      fetchProvider(
        spec(),
        (() => Promise.reject(new DOMException("timeout", "TimeoutError"))) as typeof fetch,
      ),
    "TIMEOUT",
  ));
Deno.test("oversized declared content", () =>
  rejects(
    () => boundedText(new Response("x", { headers: { "Content-Length": "3000000" } })),
    "INVALID",
  ));
Deno.test("oversized streamed content", () =>
  rejects(() => boundedText(new Response("abcd"), 3), "INVALID"));
Deno.test("truncated ONET rejected", async () =>
  await rejects(() => Promise.resolve(validate(spec(), { ...payload, total: 2 })), "INVALID"));
function deps(
  options: { cached?: unknown; fresh?: boolean; acquire?: boolean; failure?: number } = {},
): Dependencies & { calls: string[] } {
  const calls: string[] = [];
  return {
    calls,
    authenticate: () => Promise.resolve("11111111-1111-4111-8111-111111111111"),
    sleep: () => Promise.resolve(),
    rpc: (args) => {
      calls.push(String(args.op));
      return Promise.resolve(
        args.op === "limit"
          ? { allowed: true }
          : args.op === "lookup"
          ? options.cached ? { payload: options.cached, fresh: options.fresh } : null
          : args.op === "acquire"
          ? { acquired: options.acquire ?? true }
          : args.op === "store"
          ? { stored: true }
          : {},
      );
    },
    fetcher: (() => {
      calls.push("fetch");
      return Promise.resolve(
        options.failure ? new Response("{}", { status: options.failure }) : Response.json(payload),
      );
    }) as typeof fetch,
  };
}
Deno.test("fresh cache avoids upstream", async () => {
  const d = deps({ cached: payload, fresh: true });
  const r = await cachedProvider(spec(), d);
  equal(r.fromCache, true);
  equal(d.calls, ["lookup"]);
});
Deno.test("fresh result stored with lease", async () => {
  const d = deps();
  const r = await cachedProvider(spec(), d);
  equal(r.fromCache, false);
  equal(d.calls, ["lookup", "acquire", "lookup", "fetch", "store", "release"]);
});
Deno.test("stale fallback retries 3 times and never stores error", async () => {
  const d = deps({ cached: payload, fresh: false, failure: 500 });
  const r = await cachedProvider(spec(), d);
  equal(r.stale, true);
  equal(d.calls.filter((c) => c === "fetch").length, 3);
  equal(d.calls.includes("store"), false);
  equal(d.calls.at(-1), "release");
});
Deno.test("upstream auth failure not retried", async () => {
  const d = deps({ cached: payload, failure: 401 });
  await cachedProvider(spec(), d);
  equal(d.calls.filter((c) => c === "fetch").length, 1);
});
Deno.test("busy refresh uses stale cache", async () => {
  const d = deps({ cached: payload, acquire: false });
  equal((await cachedProvider(spec(), d)).stale, true);
  equal(d.calls.includes("fetch"), false);
});
Deno.test("handler enforces authentication", async () => {
  const d = deps();
  d.authenticate = () => Promise.reject(new Problem(401, "UNAUTHORIZED", "Token required"));
  const r = await createHandler(d)(new Request(url("/onet/occupations")));
  equal(r.status, 401);
  equal(d.calls, []);
});
Deno.test("handler enforces distributed rate limit", async () => {
  const d = deps();
  d.rpc = () => Promise.resolve({ allowed: false });
  const r = await createHandler(d)(new Request(url("/onet/occupations")));
  equal(r.status, 429);
  equal(r.headers.get("Retry-After"), "60");
});
Deno.test("handler rejects unsupported method", async () =>
  equal(
    (await createHandler(deps())(new Request(url("/onet/occupations"), { method: "POST" }))).status,
    405,
  ));
Deno.test("handler complete provider path", async () =>
  equal(
    (await createHandler(deps(), { onet: "fake-key" })(new Request(url("/onet/occupations"))))
      .status,
    200,
  ));
Deno.test("backend rejects unsigned header before network", async () => {
  const b = backend(
    { url: "https://example.test", publicKey: "p", serviceKey: "s" },
    (() => {
      throw new Error("network called");
    }) as typeof fetch,
  );
  try {
    await b.authenticate(new Request("https://example.test"));
    throw new Error("accepted");
  } catch (e) {
    equal((e as Problem).status, 401);
  }
});
Deno.test("backend verifies session through Auth", async () => {
  let called = false;
  const b = backend(
    { url: "https://example.test", publicKey: "p", serviceKey: "s" },
    ((u, init) => {
      called = true;
      equal(String(u), "https://example.test/auth/v1/user");
      equal(new Headers(init?.headers).get("Authorization"), "Bearer signed-session");
      return Promise.resolve(
        Response.json({
          id: "11111111-1111-4111-8111-111111111111",
          user_metadata: { role: "admin" },
        }),
      );
    }) as typeof fetch,
  );
  equal(
    await b.authenticate(
      new Request("https://example.test", { headers: { Authorization: "Bearer signed-session" } }),
    ),
    "11111111-1111-4111-8111-111111111111",
  );
  equal(called, true);
});
Deno.test("backend rejects bad session", async () => {
  const b = backend(
    { url: "https://example.test", publicKey: "p", serviceKey: "s" },
    (() => Promise.resolve(new Response(null, { status: 401 }))) as typeof fetch,
  );
  try {
    await b.authenticate(
      new Request("https://example.test", { headers: { Authorization: "Bearer forged" } }),
    );
    throw new Error("accepted");
  } catch (e) {
    equal((e as Problem).status, 401);
  }
});
Deno.test("backend rejects anonymous auth user", async () => {
  const b = backend(
    { url: "https://example.test", publicKey: "p", serviceKey: "s" },
    (() =>
      Promise.resolve(
        Response.json({ id: "11111111-1111-4111-8111-111111111111", is_anonymous: true }),
      )) as typeof fetch,
  );
  try {
    await b.authenticate(
      new Request("https://example.test", { headers: { Authorization: "Bearer signed-session" } }),
    );
    throw new Error("accepted");
  } catch (e) {
    equal((e as Problem).status, 403);
  }
});

Deno.test("integer trailing newline rejected", () => throws(() => integer("1\n", 20, 1, 100), 400));
Deno.test("occupation trailing newline rejected", () =>
  throws(() => providerSpec(url("/onet/skills/53-7062.00%0A"), { onet: "fake" }), 422));
Deno.test("lease cleanup failure does not hide successful provider result", async () => {
  const d = deps();
  const original = d.rpc;
  d.rpc = (args) =>
    args.op === "release"
      ? Promise.reject(new Problem(503, "UNAVAILABLE", "Cache unavailable"))
      : original(args);
  equal((await cachedProvider(spec(), d)).fromCache, false);
});

for (const row of liveFormatCases) {
  Deno.test(`real-format compatibility and cache: ${row.path}`, async () => {
    let stored: { payload: unknown; fresh: boolean } | null = null;
    let requests = 0;
    const handler = createHandler({
      authenticate: () => Promise.resolve("local-unit-user"),
      rpc: (args) => {
        if (args.op === "lookup") return Promise.resolve(stored);
        if (args.op === "store") stored = { payload: args.payload, fresh: true };
        return Promise.resolve({ allowed: true, acquired: true, stored: true });
      },
      fetcher: () => {
        requests++;
        return Promise.resolve(Response.json(row.value));
      },
      sleep: () => Promise.resolve(),
    }, { onet: "fake-key", cars: "test" });
    const first = await handler(new Request(url(row.path)));
    equal(first.status, 200);
    const initial = await first.json();
    equal(initial.fromCache, false);
    equal(initial.stale, false);
    equal(initial.data, row.value);
    const second = await handler(new Request(url(row.path)));
    equal(second.status, 200);
    const cached = await second.json();
    equal(cached.fromCache, true);
    equal(cached.stale, false);
    equal(cached.data, row.value);
    equal(requests, 1);
    if (row.path.startsWith("/cars/")) equal(cached.data.meta.demoMode, true);
  });
}

for (const status of [401, 429, 500, 503]) {
  for (const stale of [false, true]) {
    Deno.test(`upstream ${status}, stale cache ${stale}: safe handler response`, async () => {
      const row = liveFormatCases[2];
      let requests = 0;
      let stores = 0;
      const handler = createHandler({
        authenticate: () => Promise.resolve("local-unit-user"),
        rpc: (args) => {
          if (args.op === "lookup") {
            return Promise.resolve(stale ? { payload: row.value, fresh: false } : null);
          }
          if (args.op === "store") stores++;
          return Promise.resolve({ allowed: true, acquired: true, stored: true });
        },
        fetcher: () => {
          requests++;
          return Promise.resolve(new Response("private upstream diagnostic", { status }));
        },
        sleep: () => Promise.resolve(),
      }, { cars: "test" });
      const response = await handler(new Request(url(row.path)));
      equal(response.status, stale ? 200 : 503);
      equal(response.headers.get("Access-Control-Allow-Origin"), "*");
      const body = await response.json();
      equal(JSON.stringify(body).includes("private upstream diagnostic"), false);
      equal(requests, status === 401 ? 1 : 3);
      equal(stores, 0);
      if (stale) {
        equal(body.fromCache, true);
        equal(body.stale, true);
        equal(body.providerFailure, status === 401 ? "AUTH" : "UNAVAILABLE");
        equal(body.data, row.value);
      } else {
        equal(response.headers.get("Retry-After"), "60");
        equal(body.error.code, "UNAVAILABLE");
      }
    });
  }
}

Deno.test("CarsBase demo and live cache namespaces are separate", () => {
  const demo = providerSpec(url("/cars/models?brandId=BMW"), { cars: "test" });
  const live = providerSpec(url("/cars/models?brandId=BMW"), { cars: "local-unit-key" });
  equal(demo.key.startsWith("demo:"), true);
  equal(live.key.startsWith("live:"), true);
  equal(demo.key === live.key, false);
  equal(demo.url.searchParams.get("mark_id"), "BMW");
  equal(demo.url.searchParams.get("token"), "test");
});

Deno.test("CarsBase demo empty model list is controlled NO_DATA, never stored", async () => {
  const d = deps();
  d.fetcher = () =>
    Promise.resolve(Response.json({
      data: [],
      meta: { total: 0, count: 0, pageSize: 1000, page: 1, demoMode: true },
    }));
  const response = await createHandler(d, { cars: "test" })(
    new Request(url("/cars/models?brandId=TOYOTA")),
  );
  equal(response.status, 503);
  const body = await response.json();
  equal(body.error.code, "UNAVAILABLE");
  equal(body.error.message.endsWith("NO_DATA"), true);
  equal(d.calls.includes("store"), false);
});
