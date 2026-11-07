import { boundedText, object, Problem } from "./http.ts";
export type Rpc = (args: Record<string, unknown>) => Promise<unknown>;
export type BackendConfig = { url: string; publicKey: string; serviceKey: string };
export function backend(config: BackendConfig, fetcher: typeof fetch = fetch) {
  const auth = async (request: Request): Promise<string> => {
    const header = request.headers.get("Authorization");
    if (!header || !/^Bearer [^\s]+$/i.test(header)) {
      throw new Problem(401, "UNAUTHORIZED", "User Bearer token required");
    }
    let response: Response;
    try {
      response = await fetcher(`${config.url}/auth/v1/user`, {
        headers: { Authorization: header, apikey: config.publicKey },
        signal: AbortSignal.timeout(5000),
        redirect: "error",
      });
    } catch {
      throw new Problem(503, "UNAVAILABLE", "Auth temporarily unavailable");
    }
    if (!response.ok) {
      await response.body?.cancel();
      throw new Problem(
        response.status >= 500 ? 503 : 401,
        response.status >= 500 ? "UNAVAILABLE" : "UNAUTHORIZED",
        "User session could not be verified",
      );
    }
    let user: Record<string, unknown>;
    try {
      user = object(JSON.parse(await boundedText(response, 65536)));
    } catch {
      throw new Problem(503, "UNAVAILABLE", "Invalid Auth response");
    }
    if (
      typeof user.id !== "string" ||
      !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(user.id)
    ) {
      throw new Problem(401, "UNAUTHORIZED", "Invalid user session");
    }
    if (user.is_anonymous === true) {
      throw new Problem(403, "FORBIDDEN", "Permanent account required");
    }
    return user.id;
  };
  const rpc: Rpc = async (args) => {
    const headers: Record<string, string> = {
      apikey: config.serviceKey,
      "Content-Type": "application/json",
      "Content-Profile": "lastpay_api",
      "Accept-Profile": "lastpay_api",
    };
    if (!config.serviceKey.startsWith("sb_secret_")) {
      headers.Authorization = `Bearer ${config.serviceKey}`;
    }
    let response: Response;
    try {
      response = await fetcher(`${config.url}/rest/v1/rpc/provider_cache`, {
        method: "POST",
        headers,
        body: JSON.stringify({ args }),
        signal: AbortSignal.timeout(5000),
        redirect: "error",
      });
    } catch {
      throw new Problem(503, "UNAVAILABLE", "Cache temporarily unavailable");
    }
    if (!response.ok) {
      await response.body?.cancel();
      throw new Problem(503, "UNAVAILABLE", "Cache operation failed");
    }
    try {
      return JSON.parse(await boundedText(response, 3 * 1024 * 1024));
    } catch {
      throw new Problem(503, "UNAVAILABLE", "Invalid cache response");
    }
  };
  return { authenticate: auth, rpc };
}
