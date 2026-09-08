# Operations baseline v0.1

Day 1 uses Docker Compose and three shallow application health checks. Optional data services are placed behind the `platform` profile. No external API, AI model, or cloud account is needed for health.

The smoke script waits for bounded time, prints the failing service and response, and exits non-zero on failure. CI always tears Compose down and prints logs when smoke verification fails.

Later operations work adds OpenTelemetry propagation, metrics, structured logs, backup/restore, SLOs, and incident runbooks.

