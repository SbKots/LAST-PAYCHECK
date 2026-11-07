import countries from "./countries.json" with { type: "json" };
import { boundedText, object, Problem, ProviderError } from "./http.ts";
export type Spec = {
  provider: "WORLD_BANK" | "ONET" | "CARS_BASE";
  key: string;
  url: URL;
  headers: Record<string, string>;
  credential?: string;
  ttl: number;
  kind: string;
  country?: string;
  from?: number;
  to?: number;
  page?: number;
};
export type Secrets = { onet?: string; cars?: string };
function params(url: URL, allowed: string[], required: string[] = []): URLSearchParams {
  const q = url.searchParams;
  for (const key of q.keys()) {
    if (!allowed.includes(key) || q.getAll(key).length !== 1) {
      throw new Problem(400, "BADREQUEST", "Unknown or duplicate parameter");
    }
  }
  if (required.some((k) => !q.has(k))) throw new Problem(400, "BADREQUEST", "Missing parameter");
  return q;
}
export function integer(raw: string | null, fallback: number, min: number, max: number): number {
  if (raw === null) return fallback;
  if (!/^-?[0-9]+(?![\s\S])/.test(raw)) throw new Problem(400, "BADREQUEST", "Expected integer");
  const n = Number(raw);
  if (!Number.isSafeInteger(n) || n < min || n > max) {
    throw new Problem(422, "VALIDATIONERROR", "Integer out of range");
  }
  return n;
}
export function providerSpec(url: URL, secrets: Secrets): Spec {
  const path = url.pathname.replace(/^\/(?:functions\/v1\/)?providers(?=\/|$)/, "");
  if (path === "/worldbank/inflation") {
    const q = params(url, ["country", "yearFrom", "yearTo"], ["country", "yearFrom", "yearTo"]);
    const country = q.get("country")!;
    if (!countries.includes(country)) {
      throw new Problem(422, "VALIDATIONERROR", "ISO alpha-3 country required", "country");
    }
    const from = integer(q.get("yearFrom"), 0, 1900, 2100),
      to = integer(q.get("yearTo"), 0, 1900, 2100);
    if (to < from || to - from > 9) {
      throw new Problem(422, "VALIDATIONERROR", "Period must be at most ten years", "yearTo");
    }
    const target = new URL(
      `https://api.worldbank.org/v2/country/${country}/indicator/FP.CPI.TOTL.ZG`,
    );
    target.search = new URLSearchParams({ format: "json", date: `${from}:${to}`, per_page: "1000" })
      .toString();
    return {
      provider: "WORLD_BANK",
      key: `${country}:FP.CPI.TOTL.ZG:${from}:${to}`,
      url: target,
      headers: {},
      ttl: 30,
      kind: "inflation",
      country,
      from,
      to,
    };
  }
  if (path === "/onet/occupations" || path.startsWith("/onet/skills/")) {
    params(url, []);
    const code = path.startsWith("/onet/skills/") ? path.slice("/onet/skills/".length) : undefined;
    if (code !== undefined && !/^[0-9]{2}-[0-9]{4}\.[0-9]{2}(?![\s\S])/.test(code)) {
      throw new Problem(422, "VALIDATIONERROR", "Invalid occupation code", "occupationCode");
    }
    if (!secrets.onet) throw new Problem(503, "UNAVAILABLE", "O*NET is not configured");
    const target = new URL(
      code
        ? `https://api-v2.onetcenter.org/online/occupations/${code}/details/skills`
        : "https://api-v2.onetcenter.org/mnm/search",
    );
    target.search = new URLSearchParams({
      start: "1",
      end: "2000",
      ...(!code ? { keyword: "warehouse" } : {}),
    }).toString();
    return {
      provider: "ONET",
      key: code ? `skills:${code}:start1:end2000` : "occupations:warehouse:start1:end2000",
      url: target,
      headers: { "X-API-Key": secrets.onet },
      credential: secrets.onet,
      ttl: 7,
      kind: code ? "skills" : "occupations",
    };
  }
  if (path === "/cars/brands" || path === "/cars/models") {
    const models = path.endsWith("models");
    const q = params(url, models ? ["brandId", "page", "pageSize"] : ["page", "pageSize"]);
    const id = q.get("brandId");
    if (id !== null && !/^[A-Za-z0-9_-]{1,64}(?![\s\S])/.test(id)) {
      throw new Problem(422, "VALIDATIONERROR", "Invalid brandId", "brandId");
    }
    const page = integer(q.get("page"), 1, 1, 1000000),
      size = integer(q.get("pageSize"), 1000, 1, 1000);
    if (!secrets.cars) throw new Problem(503, "UNAVAILABLE", "CarsBase is not configured");
    const kind = models ? "models" : "marks";
    const target = new URL(`https://api.cars-base.ru/${kind}`);
    target.search = new URLSearchParams({
      token: secrets.cars,
      page: String(page),
      pageSize: String(size),
      sort_by: "id",
      ...(id !== null ? { mark_id: id } : {}),
    }).toString();
    return {
      provider: "CARS_BASE",
      key: `${secrets.cars === "test" ? "demo" : "live"}:${kind}:${
        id ?? ""
      }:page${page}:size${size}`,
      url: target,
      headers: {},
      credential: secrets.cars,
      ttl: 7,
      kind,
      page,
    };
  }
  throw new Problem(404, "NOTFOUND", "Route not found");
}
function nonblank(v: unknown): boolean {
  return (typeof v === "string" && v.trim().length > 0) ||
    typeof v === "number" && Number.isFinite(v);
}
export function validate(spec: Spec, payload: unknown): void {
  if (spec.provider === "WORLD_BANK") {
    if (!Array.isArray(payload) || payload.length !== 2) throw new ProviderError("INVALID");
    const meta = object(payload[0]);
    if (typeof meta.pages === "number" && meta.pages > 1) throw new ProviderError("INVALID");
    if (payload[1] === null || Array.isArray(payload[1]) && !payload[1].length) {
      throw new ProviderError("NO_DATA");
    }
    if (!Array.isArray(payload[1])) throw new ProviderError("INVALID");
    let valid = 0;
    for (const item of payload[1]) {
      const r = object(item), year = Number(r.date);
      if (
        r.countryiso3code !== spec.country || object(r.indicator).id !== "FP.CPI.TOTL.ZG" ||
        !Number.isInteger(year) || year < spec.from! || year > spec.to!
      ) throw new ProviderError("INVALID");
      if (r.value === null) continue;
      if (typeof r.value !== "number" || !Number.isFinite(r.value)) {
        throw new ProviderError("INVALID");
      }
      if (r.value > -100) valid++;
    }
    if (!valid) throw new ProviderError("NO_DATA");
    return;
  }
  const root = object(payload),
    rows = root[spec.provider === "ONET" ? spec.kind === "skills" ? "element" : "career" : "data"];
  if (!Array.isArray(rows)) throw new ProviderError("INVALID");
  if (!rows.length) throw new ProviderError("NO_DATA");
  const a = spec.provider === "ONET" && spec.kind === "occupations" ? "code" : "id";
  const b = spec.provider === "ONET" && spec.kind === "occupations" ? "title" : "name";
  if (
    rows.some((row) => {
      const r = object(row);
      return !nonblank(r[a]) || !nonblank(r[b]);
    })
  ) throw new ProviderError("INVALID");
  if (spec.provider === "ONET" && typeof root.total === "number" && root.total > rows.length) {
    throw new ProviderError("INVALID");
  }
  if (spec.provider === "CARS_BASE") {
    const meta = object(root.meta);
    if (meta.count !== rows.length || meta.page !== spec.page) throw new ProviderError("INVALID");
  }
}
export async function fetchProvider(spec: Spec, fetcher: typeof fetch = fetch): Promise<unknown> {
  try {
    const response = await fetcher(spec.url, {
      headers: spec.headers,
      redirect: "manual",
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) {
      await response.body?.cancel();
      if ([401, 403].includes(response.status)) throw new ProviderError("AUTH");
      if (response.status === 404) throw new ProviderError("NO_DATA");
      throw new ProviderError("UNAVAILABLE");
    }
    const text = await boundedText(response);
    let payload: unknown;
    try {
      payload = JSON.parse(text);
    } catch {
      throw new ProviderError("INVALID");
    }
    if (
      spec.credential &&
      JSON.stringify(payload).includes(JSON.stringify(spec.credential).slice(1, -1))
    ) throw new ProviderError("INVALID");
    validate(spec, payload);
    return payload;
  } catch (error) {
    if (error instanceof ProviderError) throw error;
    throw new ProviderError(
      error instanceof DOMException && ["TimeoutError", "AbortError"].includes(error.name)
        ? "TIMEOUT"
        : "UNAVAILABLE",
    );
  }
}
