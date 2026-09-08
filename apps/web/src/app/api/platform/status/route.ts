import { isPlatformStatus } from "../../../../lib/platform-status";

export async function GET() {
  const coreApiUrl = process.env.CORE_API_URL ?? "http://localhost:8080";

  try {
    const upstream = await fetch(`${coreApiUrl}/api/v1/platform/status`, {
      cache: "no-store",
      signal: AbortSignal.timeout(3_000),
    });
    if (!upstream.ok) {
      throw new Error(`Core API returned ${upstream.status}`);
    }
    const payload: unknown = await upstream.json();
    if (!isPlatformStatus(payload)) {
      throw new Error("Core API response did not match platform status v1");
    }
    return Response.json(payload, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return Response.json(
      { status: "DOWN", service: "web", reason: "platform-contract-unavailable" },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}
