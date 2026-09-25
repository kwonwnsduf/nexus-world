import { describe, expect, it } from "vitest";
import { normalizeDemoResult } from "./supply-chain-demo";

const grounded = {
  contractVersion: "v2",
  status: "INSUFFICIENT_DATA",
  worldVersionId: "world-version-1",
  extraction: { originalQuery: "대만 반도체 공급이 50% 감소하면?", target: "대만 반도체",
    metric: "SUPPLY", change: -0.5, duration: null, optionalPolicy: null, confidence: 0.95 },
  target: { entityId: "entity-1", entityType: "COUNTRY",
    naturalKey: "country:TW", displayName: "Taiwan" },
  shock: { metric: "SUPPLY", change: -0.5, duration: null, basisType: "USER_ASSUMPTION" },
  worldManifest: { asOfDate: "2026-09-23", createdAt: "2026-09-23T00:00:00Z", ontologyVersion: "v1",
    simulationRuleVersion: "relationship-graph-v1", graphProjectionVersion: "neo4j-v1",
    retrievalIndexStatus: "READY", retrievalDocumentCount: 1,
    expectedRetrievalDocumentCount: 1, sourceSnapshotIds: ["run-1"] },
  provenance: { shock: "USER_ASSUMPTION", baselineValues: [{}], relationshipParameters: [{}] },
  graphRag: { answer: "Best supported path", paths: [],
    graphEvidence: [{ evidenceId: "e-1" }], textCitations: [{ chunkId: "c-1" }] },
  dataQuality: { ready: false, missing: ["company capacity"],
    message: "blocked", policyApplied: false },
  simulation: null,
};

describe("grounded supply-chain response", () => {
  it("keeps evidence visible while quantitative execution is blocked", () => {
    const result = normalizeDemoResult(grounded);
    expect(result.status).toBe("INSUFFICIENT_DATA");
    expect(result.graph).toMatchObject({ evidenceCount: 1, citationCount: 1 });
    expect(result.worldManifest.retrievalIndexStatus).toBe("READY");
    expect(result.provenance.relationshipParameterCount).toBe(1);
    expect(result.branches).toEqual([]);
  });

  it("rejects the removed hard-coded v1 response", () => {
    expect(() => normalizeDemoResult({ contractVersion: "v1" }))
      .toThrow("invalid grounded workflow");
  });
});
