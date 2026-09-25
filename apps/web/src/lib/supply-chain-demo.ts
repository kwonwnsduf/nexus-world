export type DemoBranch = {
  name: string;
  seed: number;
  resultHash: string;
  metrics: Record<string, number>;
  invariantsPassed: boolean;
};

export type SupplyChainDemoResult = {
  contractVersion: "v2";
  status: "COMPLETED" | "INSUFFICIENT_DATA";
  worldVersionId: string;
  extraction: {
    originalQuery: string;
    target: string;
    metric: string;
    change: number;
    duration: number | null;
    optionalPolicy: string | null;
    confidence: number;
  };
  target: { entityId: string; entityType: string; naturalKey: string; displayName: string };
  shock: { metric: string; change: number; duration: number | null; basisType: string };
  worldManifest: {
    asOfDate: string; createdAt: string; ontologyVersion: string; simulationRuleVersion: string;
    graphProjectionVersion: string; retrievalIndexStatus: string; sourceSnapshotCount: number;
    retrievalDocumentCount: number; expectedRetrievalDocumentCount: number;
  };
  provenance: { baselineValueCount: number; relationshipParameterCount: number; shock: string };
  graph: {
    answer: string;
    paths: Array<{ score: number; nodes: Array<{ displayName: string }>; relationships: Array<{ relationshipType: string }> }>;
    evidence: Array<{ sourceUri: string | null; evidenceId: string | null; ownerKind: string }>;
    citations: Array<{ title: string; sourceUri: string | null; sectionPath: string | null }>;
    evidenceCount: number;
    citationCount: number;
  };
  dataQuality: { ready: boolean; missing: string[]; message: string; policyApplied: boolean };
  scenarioId: string | null;
  branches: DemoBranch[];
};

type JsonRecord = Record<string, unknown>;

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requiredRecord(value: unknown, name: string): JsonRecord {
  if (!isRecord(value)) throw new Error(`Core API response is missing ${name}`);
  return value;
}

export function normalizeDemoResult(value: unknown): SupplyChainDemoResult {
  const root = requiredRecord(value, "workflow");
  if (root.contractVersion !== "v2" || (root.status !== "COMPLETED"
      && root.status !== "INSUFFICIENT_DATA") || typeof root.worldVersionId !== "string") {
    throw new Error("Core API returned an invalid grounded workflow response");
  }
  const extraction = requiredRecord(root.extraction, "extraction");
  const target = requiredRecord(root.target, "target");
  const shock = requiredRecord(root.shock, "shock");
  const worldManifest = requiredRecord(root.worldManifest, "world manifest");
  const provenance = requiredRecord(root.provenance, "provenance summary");
  const graphRag = requiredRecord(root.graphRag, "GraphRAG result");
  const quality = requiredRecord(root.dataQuality, "data quality");
  if (typeof extraction.originalQuery !== "string" || typeof extraction.target !== "string"
      || typeof extraction.metric !== "string" || typeof extraction.change !== "number"
      || typeof extraction.confidence !== "number" || typeof target.entityId !== "string"
      || typeof target.entityType !== "string" || typeof target.naturalKey !== "string"
      || typeof target.displayName !== "string" || typeof shock.basisType !== "string") {
    throw new Error("Core API returned an invalid extraction or resolved target");
  }
  const paths = Array.isArray(graphRag.paths) ? graphRag.paths.filter(isRecord).map((path) => ({
    score: typeof path.score === "number" ? path.score : 0,
    nodes: Array.isArray(path.nodes) ? path.nodes.filter(isRecord).map((node) => ({
      displayName: typeof node.displayName === "string" ? node.displayName : "unknown",
    })) : [],
    relationships: Array.isArray(path.relationships)
      ? path.relationships.filter(isRecord).map((edge) => ({
          relationshipType: typeof edge.relationshipType === "string" ? edge.relationshipType : "UNKNOWN",
        })) : [],
  })) : [];
  const simulation = isRecord(root.simulation) ? root.simulation : undefined;
  const runs = simulation && Array.isArray(simulation.runs) ? simulation.runs : [];
  const branches = runs.map((raw): DemoBranch => {
    const run = requiredRecord(raw, "run");
    const snapshots = Array.isArray(run.snapshots) ? run.snapshots : [];
    const finalSnapshot = requiredRecord(snapshots.at(-1), "final snapshot");
    const rawMetrics = requiredRecord(finalSnapshot.metrics, "metrics");
    const metrics = Object.fromEntries(Object.entries(rawMetrics)
      .filter((entry): entry is [string, number] => typeof entry[1] === "number"));
    const invariants = requiredRecord(run.invariants, "invariants");
    return {
      name: String(run.branchName), seed: Number(run.seed), resultHash: String(run.resultHash), metrics,
      invariantsPassed: Object.values(invariants).length > 0
        && Object.values(invariants).every((entry) => entry === true),
    };
  });
  return {
    contractVersion: "v2",
    status: root.status,
    worldVersionId: root.worldVersionId,
    extraction: {
      originalQuery: extraction.originalQuery,
      target: extraction.target,
      metric: extraction.metric,
      change: extraction.change,
      duration: typeof extraction.duration === "number" ? extraction.duration : null,
      optionalPolicy: typeof extraction.optionalPolicy === "string" ? extraction.optionalPolicy : null,
      confidence: extraction.confidence,
    },
    target: {
      entityId: target.entityId, entityType: target.entityType,
      naturalKey: target.naturalKey, displayName: target.displayName,
    },
    shock: {
      metric: String(shock.metric), change: Number(shock.change),
      duration: typeof shock.duration === "number" ? shock.duration : null,
      basisType: shock.basisType,
    },
    worldManifest: {
      asOfDate: typeof worldManifest.asOfDate === "string" ? worldManifest.asOfDate : "unknown",
      createdAt: typeof worldManifest.createdAt === "string" ? worldManifest.createdAt : "unknown",
      ontologyVersion: typeof worldManifest.ontologyVersion === "string" ? worldManifest.ontologyVersion : "unknown",
      simulationRuleVersion: typeof worldManifest.simulationRuleVersion === "string" ? worldManifest.simulationRuleVersion : "unknown",
      graphProjectionVersion: typeof worldManifest.graphProjectionVersion === "string" ? worldManifest.graphProjectionVersion : "unknown",
      retrievalIndexStatus: typeof worldManifest.retrievalIndexStatus === "string" ? worldManifest.retrievalIndexStatus : "unknown",
      sourceSnapshotCount: Array.isArray(worldManifest.sourceSnapshotIds) ? worldManifest.sourceSnapshotIds.length : 0,
      retrievalDocumentCount: typeof worldManifest.retrievalDocumentCount === "number"
        ? worldManifest.retrievalDocumentCount : 0,
      expectedRetrievalDocumentCount: typeof worldManifest.expectedRetrievalDocumentCount === "number"
        ? worldManifest.expectedRetrievalDocumentCount : 0,
    },
    provenance: {
      baselineValueCount: Array.isArray(provenance.baselineValues) ? provenance.baselineValues.length : 0,
      relationshipParameterCount: Array.isArray(provenance.relationshipParameters)
        ? provenance.relationshipParameters.length : 0,
      shock: typeof provenance.shock === "string" ? provenance.shock : "unknown",
    },
    graph: {
      answer: typeof graphRag.answer === "string" ? graphRag.answer : "",
      paths,
      evidence: Array.isArray(graphRag.graphEvidence) ? graphRag.graphEvidence.filter(isRecord).map((item) => ({
        sourceUri: typeof item.sourceUri === "string" ? item.sourceUri : null,
        evidenceId: typeof item.evidenceId === "string" ? item.evidenceId : null,
        ownerKind: typeof item.ownerKind === "string" ? item.ownerKind : "UNKNOWN",
      })) : [],
      citations: Array.isArray(graphRag.textCitations) ? graphRag.textCitations.filter(isRecord).map((item) => ({
        title: typeof item.title === "string" ? item.title : "Evidence",
        sourceUri: typeof item.sourceUri === "string" ? item.sourceUri : null,
        sectionPath: typeof item.sectionPath === "string" ? item.sectionPath : null,
      })) : [],
      evidenceCount: Array.isArray(graphRag.graphEvidence) ? graphRag.graphEvidence.length : 0,
      citationCount: Array.isArray(graphRag.textCitations) ? graphRag.textCitations.length : 0,
    },
    dataQuality: {
      ready: quality.ready === true,
      missing: Array.isArray(quality.missing) ? quality.missing.filter((item): item is string => typeof item === "string") : [],
      message: typeof quality.message === "string" ? quality.message : "",
      policyApplied: quality.policyApplied === true,
    },
    scenarioId: simulation && typeof simulation.scenarioId === "string" ? simulation.scenarioId : null,
    branches,
  };
}
