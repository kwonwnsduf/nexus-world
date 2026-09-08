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
}

const schema = JSON.parse(
  await readFile(path.join(contractRoot, "schemas", "platform-contract-v1.json"), "utf8"),
);
if (!schema.$defs?.AiCapabilities || !schema.$defs?.PlatformStatus) {
  throw new Error("platform-contract-v1.json is missing required definitions");
}

console.log("Contract documents are valid.");

