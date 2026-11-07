import { backend } from "../_shared/backend.ts";
import { cachedProvider, type Dependencies } from "../_shared/cache.ts";
import { jsonResponse, Problem, problemResponse } from "../_shared/http.ts";
import { providerSpec, type Secrets } from "../_shared/providers.ts";
export function createHandler(deps: Dependencies, secrets: Secrets = {}) {
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, OPTIONS",
          "Access-Control-Allow-Headers": "authorization, apikey, content-type",
        },
      });
    }
    try {
      if (request.method !== "GET") throw new Problem(405, "METHODNOTALLOWED", "GET required");
      const userId = await deps.authenticate(request);
      const limit = await deps.rpc({ op: "limit", userId }) as { allowed: boolean };
      if (!limit.allowed) throw new Problem(429, "RATELIMITED", "Too many integration requests");
      const spec = providerSpec(new URL(request.url), secrets);
      const result = await cachedProvider(spec, deps);
      const response = jsonResponse(result);
      response.headers.set("Access-Control-Allow-Origin", "*");
      return response;
    } catch (error) {
      const response = problemResponse(error);
      response.headers.set("Access-Control-Allow-Origin", "*");
      return response;
    }
  };
}
if (import.meta.main) {
  const url = Deno.env.get("SUPABASE_URL"),
    publicKey = Deno.env.get("SB_PUBLISHABLE_KEY") ?? Deno.env.get("SUPABASE_ANON_KEY"),
    serviceKey = Deno.env.get("SB_SECRET_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !publicKey || !serviceKey) throw new Error("Missing backend configuration");
  Deno.serve(
    createHandler(backend({ url, publicKey, serviceKey }), {
      onet: Deno.env.get("ONET_API_KEY"),
      cars: Deno.env.get("CARS_API_TOKEN"),
    }),
  );
}
