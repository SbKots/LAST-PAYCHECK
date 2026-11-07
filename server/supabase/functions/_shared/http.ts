// Stable domain envelope; do not serialize upstream URLs, errors, credentials or JWTs.
export class Problem extends Error {
  constructor(public status: number, public code: string, message: string, public field?: string) {
    super(message);
  }
}
export function problemResponse(error: unknown): Response {
  const e = error instanceof Problem ? error : new Problem(500, "INTERNALERROR", "Internal error");
  const headers = new Headers({ "Content-Type": "application/json", "Cache-Control": "no-store" });
  if (e.status === 401) headers.set("WWW-Authenticate", "Bearer");
  if (e.status === 429 || e.status === 503) headers.set("Retry-After", "60");
  if (e.status === 405) headers.set("Allow", "GET, OPTIONS");
  return new Response(
    JSON.stringify({
      error: {
        code: e.code,
        message: e.message.slice(0, 300),
        requestId: crypto.randomUUID(),
        details: e.field ? [{ field: e.field, message: e.message }] : [],
      },
    }),
    { status: e.status, headers },
  );
}
export function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
export async function boundedText(response: Response, max = 2 * 1024 * 1024): Promise<string> {
  if (Number(response.headers.get("Content-Length")) > max) {
    await response.body?.cancel();
    throw new ProviderError("INVALID");
  }
  const reader = response.body?.getReader();
  if (!reader) return "";
  const parts: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > max) {
        await reader.cancel();
        throw new ProviderError("INVALID");
      }
      parts.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    bytes.set(part, offset);
    offset += part.length;
  }
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw new ProviderError("INVALID");
  }
}
export type Failure = "TIMEOUT" | "UNAVAILABLE" | "AUTH" | "INVALID" | "NO_DATA";
export class ProviderError extends Error {
  constructor(public reason: Failure) {
    super(reason);
  }
}
export function object(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new ProviderError("INVALID");
  }
  return value as Record<string, unknown>;
}
