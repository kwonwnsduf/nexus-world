import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

function run(name: string) {
  return {
    branchName: name, seed: 1, status: "COMPLETED", resultHash: "a".repeat(64),
    snapshots: [{ metrics: { totalProduction: 10 } }],
    invariants: { accountingReconciled: true },
  };
}

describe("supply-chain BFF", () => {
  it("keeps the workflow disabled unless explicitly configured", async () => {
    vi.stubEnv("DEMO_MODE_ENABLED", "false");
    const { POST } = await import("./route");
    const request = new Request("http://test", {
      method: "POST", body: JSON.stringify({ query: "Taiwan supply -50%" }),
    });
    expect((await POST(request)).status).toBe(503);
  });

  it("passes the bearer token through the grounded Core workflow", async () => {
    vi.stubEnv("DEMO_MODE_ENABLED", "true");
    vi.stubEnv("DEMO_USERNAME", "demo");
    vi.stubEnv("DEMO_PASSWORD", "secret");
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ accessToken: "access", refreshToken: "refresh" }))
      .mockResolvedValueOnce(Response.json({
        contractVersion: "v2", status: "COMPLETED", worldVersionId: "world-version-1",
        extraction: { originalQuery: "NVIDIA production -50%", target: "NVIDIA",
          metric: "PRODUCTION", change: -0.5, duration: null,
          optionalPolicy: null, confidence: 0.96 },
        target: { entityId: "entity-1", entityType: "COMPANY",
          naturalKey: "company:nvidia", displayName: "NVIDIA" },
        shock: { metric: "PRODUCTION", change: -0.5,
          duration: null, basisType: "USER_ASSUMPTION" },
        worldManifest: { asOfDate: "2026-09-23", createdAt: "2026-09-23T00:00:00Z", ontologyVersion: "v1",
          simulationRuleVersion: "relationship-graph-v1", graphProjectionVersion: "neo4j-v1",
          retrievalIndexStatus: "READY", retrievalDocumentCount: 1,
          expectedRetrievalDocumentCount: 1, sourceSnapshotIds: ["run-1"] },
        provenance: { shock: "USER_ASSUMPTION", baselineValues: [{}],
          relationshipParameters: [{}] },
        graphRag: { answer: "grounded", paths: [], graphEvidence: [], textCitations: [] },
        dataQuality: { ready: true, missing: [], message: "ready", policyApplied: false },
        simulation: { scenarioId: "scenario-1",
          runs: [run("A: baseline"), run("B: shock"), run("C: no policy")] },
      }, { status: 201 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    const { POST } = await import("./route");
    const response = await POST(new Request("http://test", {
      method: "POST", body: JSON.stringify({ query: "NVIDIA production -50%" }),
    }));

    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ contractVersion: "v2", scenarioId: "scenario-1" });
    expect(fetchMock.mock.calls[1][0]).toContain("/scenario-runs/from-query");
    expect(fetchMock.mock.calls[1][1]?.headers).toMatchObject({ Authorization: "Bearer access" });
  });
});
