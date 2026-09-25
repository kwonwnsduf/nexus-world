import { readFile, readdir } from "node:fs/promises";
import path from "node:path";

const contractRoot = path.resolve("contracts");
const requiredPaths = new Map([
  ["ai-service-v1.json", "/api/v1/platform/capabilities"],
  ["core-api-v1.json", "/api/v1/platform/status"],
]);

for (const file of await readdir(path.join(contractRoot, "openapi"))) {
  if (!file.endsWith(".json")) continue;
  const document = JSON.parse(await readFile(path.join(contractRoot, "openapi", file), "utf8"));
  if (document.openapi !== "3.1.0" || !document.info?.version || !document.paths) {
    throw new Error(`${file} is not a valid NEXUS WORLD OpenAPI 3.1 document`);
  }
  const requiredPath = requiredPaths.get(file);
  if (requiredPath && !document.paths[requiredPath]?.get) {
    throw new Error(`${file} is missing GET ${requiredPath}`);
  }
  if (file === "core-api-v1.json") {
    if (!document.paths["/api/v1/auth/login"]?.post) {
      throw new Error(`${file} is missing POST /api/v1/auth/login`);
    }
    if (!document.paths["/api/v1/auth/me"]?.get) {
      throw new Error(`${file} is missing GET /api/v1/auth/me`);
    }
    if (!document.paths["/api/v1/auth/refresh"]?.post) {
      throw new Error(`${file} is missing POST /api/v1/auth/refresh`);
    }
    if (!document.paths["/api/v1/auth/logout"]?.post) {
      throw new Error(`${file} is missing POST /api/v1/auth/logout`);
    }
    for (const path of ["/api/v1/sources", "/api/v1/evidence", "/api/v1/assumptions", "/api/v1/provenance-links"]) {
      if (!document.paths[path]?.post) throw new Error(`${file} is missing POST ${path}`);
    }
    if (!document.paths["/api/v1/ontology"]?.get) throw new Error(`${file} is missing GET /api/v1/ontology`);
    if (!document.paths["/api/v1/admin/ingestions/{source}"]?.post) throw new Error(`${file} is missing ingestion start contract`);
    if (!document.paths["/api/v1/admin/ingestions/{id}"]?.get) throw new Error(`${file} is missing ingestion status contract`);
    if (!document.paths["/api/v1/ontology/actions/validate"]?.post) {
      throw new Error(`${file} is missing POST /api/v1/ontology/actions/validate`);
    }
    if (!document.paths["/api/v1/world-versions/{versionId}/graph"]?.get) throw new Error(`${file} is missing world graph read contract`);
    if (!document.paths["/api/v1/world-versions/{versionId}/graph/entities/resolve"]?.post) throw new Error(`${file} is missing entity resolution contract`);
    if (!document.paths["/api/v1/world-versions/{versionId}/graph/projections"]?.post) throw new Error(`${file} is missing Neo4j projection contract`);
    if (!document.paths["/api/v1/world-versions/{versionId}/graph/paths/{rootEntityId}"]?.get) throw new Error(`${file} is missing bounded graph path contract`);
    if (!document.paths["/api/v1/world-versions/{versionId}/graph/entities/matches"]?.get) throw new Error(`${file} is missing graph alias match contract`);
    if (!document.paths["/api/v1/worlds"]?.post) throw new Error(`${file} is missing world creation contract`);
    if (!document.paths["/api/v1/world-versions/{versionId}/parallel-simulations"]?.post) throw new Error(`${file} is missing parallel simulation contract`);
    if (!document.paths["/api/v1/parallel-simulations/{scenarioId}"]?.get) throw new Error(`${file} is missing parallel simulation read contract`);
    if (!document.paths["/api/v1/scenario-runs/from-query"]?.post) throw new Error(`${file} is missing natural-language scenario workflow contract`);
  }
  if (file === "ai-service-v1.json") {
    if (!document.paths["/api/v1/graphrag/query"]?.post) throw new Error(`${file} is missing GraphRAG query contract`);
    if (!document.paths["/api/v1/simulations/execute"]?.post) throw new Error(`${file} is missing deterministic simulation contract`);
    if (!document.paths["/api/v1/scenarios/interpret"]?.post) throw new Error(`${file} is missing scenario interpretation contract`);
  }
}

const simulationSchema = JSON.parse(await readFile(path.join(contractRoot, "schemas", "simulation-contract-v1.json"), "utf8"));
for (const definition of ["CompanyState", "SupplyLink", "IndustrialShock", "SimulationRequest", "SimulationResult", "GraphNode", "GraphRelationship", "GraphShock", "GraphSimulationRequest", "GraphSimulationResult", "WorldBaseline", "CreateWorld", "ParallelSimulationRequest", "WorldVersionResponse", "ParallelResult"]) {
  if (!simulationSchema.$defs?.[definition]) throw new Error(`simulation-contract-v1.json is missing ${definition}`);
}

const schema = JSON.parse(
  await readFile(path.join(contractRoot, "schemas", "platform-contract-v1.json"), "utf8"),
);
if (!schema.$defs?.AiCapabilities || !schema.$defs?.PlatformStatus) {
  throw new Error("platform-contract-v1.json is missing required definitions");
}

const provenanceSchema = JSON.parse(
  await readFile(path.join(contractRoot, "schemas", "provenance-contract-v1.json"), "utf8"),
);
for (const definition of ["CreateSource", "Source", "CreateEvidence", "Evidence", "CreateAssumption", "Assumption", "CreateProvenanceLink", "ProvenanceLink"]) {
  if (!provenanceSchema.$defs?.[definition]) throw new Error(`provenance-contract-v1.json is missing ${definition}`);
}

const ontologySchema = JSON.parse(await readFile(path.join(contractRoot, "schemas", "ontology-contract-v1.json"), "utf8"));
for (const definition of [
  "PropertyDataType",
  "EntityType",
  "RelationshipType",
  "PropertyType",
  "ActionType",
  "ValidateAction",
  "ActionValidation",
  "CreateGraphEntity",
  "GraphEntity",
  "CreateGraphRelationship",
  "GraphRelationship",
  "Ontology",
  "WorldGraph",
  "ResolutionIdentifier",
  "ResolveGraphEntity",
  "EntityResolution",
  "GraphProjection",
  "GraphPaths",
  "GraphNodeMatches",
]) {
  if (!ontologySchema.$defs?.[definition]) throw new Error(`ontology-contract-v1.json is missing ${definition}`);
}

const graphRagSchema = JSON.parse(await readFile(path.join(contractRoot, "schemas", "graphrag-contract-v1.json"), "utf8"));
for (const definition of ["GraphRagQuery", "GraphEvidence", "RankedPath", "GraphRagResult"]) {
  if (!graphRagSchema.$defs?.[definition]) throw new Error(`graphrag-contract-v1.json is missing ${definition}`);
}

const scenarioWorkflowSchema = JSON.parse(await readFile(path.join(contractRoot, "schemas", "scenario-workflow-contract-v1.json"), "utf8"));
for (const definition of ["ScenarioQuery", "ScenarioCandidate", "ShockDefinition", "ScenarioWorkflowResult"]) {
  if (!scenarioWorkflowSchema.$defs?.[definition]) throw new Error(`scenario-workflow-contract-v1.json is missing ${definition}`);
}

const ingestionSchema = JSON.parse(await readFile(path.join(contractRoot, "schemas", "ingestion-contract-v1.json"), "utf8"));
for (const definition of ["SourceSystem", "StartIngestion", "IngestionResult"]) {
  if (!ingestionSchema.$defs?.[definition]) throw new Error(`ingestion-contract-v1.json is missing ${definition}`);
}

console.log("Contract documents are valid.");
