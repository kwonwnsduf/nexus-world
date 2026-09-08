export type AiCapabilities = {
  status: string;
  service: string;
  contractVersion: string;
  capabilities: string[];
};

export type PlatformStatus = {
  status: string;
  service: string;
  contractVersion: string;
  downstream: AiCapabilities;
};

export function isPlatformStatus(value: unknown): value is PlatformStatus {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<PlatformStatus>;
  return (
    candidate.status === "UP" &&
    candidate.service === "core-api" &&
    candidate.contractVersion === "v1" &&
    candidate.downstream?.service === "ai-service" &&
    candidate.downstream.contractVersion === "v1" &&
    Array.isArray(candidate.downstream.capabilities)
  );
}

