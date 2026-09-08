import { afterEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("platform status BFF", () => {
  it("proxies a valid Core API v1 response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: "UP",
            service: "core-api",
            contractVersion: "v1",
            downstream: {
              status: "UP",
              service: "ai-service",
              contractVersion: "v1",
              capabilities: ["deterministic-simulation"],
            },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const response = await GET();

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      service: "core-api",
      downstream: { service: "ai-service" },
    });
  });

  it("returns 502 for an invalid upstream contract", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ status: "UP" })));

    const response = await GET();

    expect(response.status).toBe(502);
  });
});

