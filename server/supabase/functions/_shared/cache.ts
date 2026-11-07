import { type Rpc } from "./backend.ts";
import { type Failure, Problem, ProviderError } from "./http.ts";
import { fetchProvider, type Spec, validate } from "./providers.ts";
export type Dependencies = {
  authenticate: (request: Request) => Promise<string>;
  rpc: Rpc;
  fetcher?: typeof fetch;
  sleep?: (ms: number) => Promise<void>;
};
export async function cachedProvider(
  spec: Spec,
  deps: Dependencies,
): Promise<Record<string, unknown>> {
  const hash = Array.from(
    new Uint8Array(
      await crypto.subtle.digest(
        "SHA-256",
        new TextEncoder().encode(`${spec.provider}:${spec.key}`),
      ),
    ),
  ).map((b) => b.toString(16).padStart(2, "0")).join("");
  const key = { provider: spec.provider, hash };
  const raw = await deps.rpc({ op: "lookup", ...key });
  const cached = raw as { payload: unknown; fresh: boolean } | null;
  if (cached) {
    try {
      validate(spec, cached.payload);
    } catch {
      throw new Problem(503, "UNAVAILABLE", "Invalid stored provider data");
    }
  }
  if (cached?.fresh) {
    return { provider: spec.provider, fromCache: true, stale: false, data: cached.payload };
  }
  const owner = crypto.randomUUID();
  const acquired = await deps.rpc({ op: "acquire", ...key, owner }) as { acquired: boolean };
  if (!acquired.acquired) {
    if (cached) {
      return {
        provider: spec.provider,
        fromCache: true,
        stale: true,
        providerFailure: "UNAVAILABLE",
        data: cached.payload,
      };
    }
    throw new Problem(503, "UNAVAILABLE", "Provider refresh in progress");
  }
  let reason: Failure = "UNAVAILABLE";
  try {
    // Another worker may have populated the cache between lookup and lease acquisition.
    const latest = await deps.rpc({ op: "lookup", ...key }) as typeof cached;
    if (latest?.fresh) {
      validate(spec, latest.payload);
      return { provider: spec.provider, fromCache: true, stale: false, data: latest.payload };
    }
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const payload = await fetchProvider(spec, deps.fetcher);
        const result = await deps.rpc({ op: "store", ...key, owner, payload, ttl: spec.ttl }) as {
          stored: boolean;
        };
        if (!result.stored) throw new Problem(503, "UNAVAILABLE", "Provider lease expired");
        return { provider: spec.provider, fromCache: false, stale: false, data: payload };
      } catch (error) {
        if (!(error instanceof ProviderError)) throw error;
        reason = error.reason;
        if (!["TIMEOUT", "UNAVAILABLE"].includes(reason) || attempt === 2) break;
        await (deps.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms))))(
          (attempt + 1) * 1000,
        );
      }
    }
  } finally {
    try {
      await deps.rpc({ op: "release", ...key, owner });
    } catch {
      // The lease expires automatically; failed cleanup must not hide a valid response.
    }
  }
  if (cached) {
    return {
      provider: spec.provider,
      fromCache: true,
      stale: true,
      providerFailure: reason,
      data: cached.payload,
    };
  }
  throw new Problem(503, "UNAVAILABLE", `External source unavailable: ${reason}`);
}
