import {
  normalizeDemoResult,
  type SupplyChainDemoResult,
} from "../../../../lib/supply-chain-demo";

export const dynamic = "force-dynamic";

const CACHE_MILLISECONDS = 5 * 60 * 1000;
let cached: { expiresAt: number; value: SupplyChainDemoResult } | undefined;
let inFlight: { query: string; promise: Promise<SupplyChainDemoResult> } | undefined;

type TokenResponse = { accessToken?: unknown; refreshToken?: unknown };

class CoreRequestError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

async function coreRequest(path: string, init: RequestInit = {}): Promise<unknown> {
  const coreApiUrl = process.env.CORE_API_URL ?? "http://localhost:8080";
  const response = await fetch(`${coreApiUrl}${path}`, {
    ...init,
    cache: "no-store",
    signal: AbortSignal.timeout(90_000),
    headers: { "Content-Type": "application/json", ...init.headers },
  });
  if (!response.ok) {
    let detail = `Core API ${path} returned ${response.status}`;
    try {
      const problem: unknown = await response.json();
      if (typeof problem === "object" && problem !== null && "detail" in problem
          && typeof problem.detail === "string") detail = problem.detail;
    } catch { /* retain the bounded fallback */ }
    throw new CoreRequestError(response.status, detail);
  }
  if (response.status === 204) return undefined;
  return response.json();
}

async function executeDemo(query: string): Promise<SupplyChainDemoResult> {
  const username = process.env.DEMO_USERNAME;
  const password = process.env.DEMO_PASSWORD;
  if (!username || !password) throw new Error("Demo credentials are not configured");

  const login = (await coreRequest("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  })) as TokenResponse;
  if (typeof login.accessToken !== "string" || typeof login.refreshToken !== "string") {
    throw new Error("Core API did not issue a demo token");
  }
  const authorization = { Authorization: `Bearer ${login.accessToken}` };

  try {
    const simulation = await coreRequest("/api/v1/scenario-runs/from-query", {
      method: "POST",
      headers: authorization,
      body: JSON.stringify({ query }),
    });
    return normalizeDemoResult(simulation);
  } finally {
    await coreRequest("/api/v1/auth/logout", {
      method: "POST",
      headers: authorization,
      body: JSON.stringify({ refreshToken: login.refreshToken }),
    }).catch(() => undefined);
  }
}

export async function POST(request: Request) {
  if (process.env.DEMO_MODE_ENABLED !== "true") {
    return Response.json(
      { title: "Demo disabled", status: 503, detail: "Supply-chain demo mode is not enabled" },
      { status: 503 },
    );
  }
  let query: string;
  try {
    const body: unknown = await request.json();
    query = isQueryRequest(body) ? body.query.trim() : "";
  } catch {
    query = "";
  }
  if (query.length < 3 || query.length > 500) {
    return Response.json(
      { title: "Invalid scenario", status: 400, detail: "Enter a scenario between 3 and 500 characters" },
      { status: 400 },
    );
  }
  if (cached && cached.expiresAt > Date.now() && cached.value.extraction.originalQuery === query) {
    return Response.json(cached.value);
  }

  try {
    if (!inFlight || inFlight.query !== query) {
      inFlight = { query, promise: executeDemo(query) };
    }
    const value = await inFlight.promise;
    cached = { value, expiresAt: Date.now() + CACHE_MILLISECONDS };
    return Response.json(value, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof CoreRequestError && [400, 404, 422].includes(error.status)) {
      return Response.json(
        { title: "Scenario not mapped", status: 422, detail: error.message },
        { status: 422 },
      );
    }
    return Response.json(
      { title: "Demo unavailable", status: 502, detail: "The connected simulation path did not complete" },
      { status: 502 },
    );
  } finally {
    if (inFlight?.query === query) inFlight = undefined;
  }
}

function isQueryRequest(value: unknown): value is { query: string } {
  return typeof value === "object" && value !== null && "query" in value
    && typeof value.query === "string";
}
