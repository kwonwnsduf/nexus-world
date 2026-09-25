# Operations baseline v0.1

Day 1 uses Docker Compose and three shallow application health checks. Optional data services are placed behind the `platform` profile. No external API, AI model, or cloud account is needed for health.

The smoke script waits for bounded time, prints the failing service and response, and exits non-zero on failure. CI always tears Compose down and prints logs when smoke verification fails.

Day 5 adds a cost-conscious AWS deployment path: Terraform provisions one EC2 instance in a public subnet, Nginx exposes the application, Systems Manager replaces inbound SSH, SSM Parameter Store holds runtime secrets, and a private S3 bucket stores releases and daily PostgreSQL backups. This is a development/demo topology and has a single point of failure.

RDS, a load balancer, private application subnets/NAT, and ECS or k3s remain explicit scaling decisions. They are added only after resource baselines, recovery objectives, or availability requirements justify them.

Later operations work adds OpenTelemetry propagation, metrics, structured logs, SLOs, and incident runbooks.

Day 21 turns the supply-chain demo into a deployment gate. A release is promoted only after the Nginx → Web → Core →
AI → PostgreSQL path completes three deterministic parallel branches with all invariants passing. The deployment then
stores CPU, memory, disk, and per-container usage as JSON under `/opt/nexus-world/shared/baselines/` and in the private
artifact bucket under `baselines/`. See `docs/days/DAY-21.md` for the exact acceptance criteria.

The production AI container receives both `CORE_API_URL` and `RAG_DATABASE_URL`. The first closes the GraphRAG
AI→Core loop; the second persists retrieval chunks in the existing PostgreSQL deployment instead of process memory.
OpenAI and Neo4j Aura credentials are optional SSM parameters. If no Neo4j URI/password exists, projection remains
explicitly disabled rather than pretending that the graph store is connected.
