# Operations baseline v0.1

Day 1 uses Docker Compose and three shallow application health checks. Optional data services are placed behind the `platform` profile. No external API, AI model, or cloud account is needed for health.

The smoke script waits for bounded time, prints the failing service and response, and exits non-zero on failure. CI always tears Compose down and prints logs when smoke verification fails.

Day 5 adds a cost-conscious AWS deployment path: Terraform provisions one EC2 instance in a public subnet, Nginx exposes the application, Systems Manager replaces inbound SSH, SSM Parameter Store holds runtime secrets, and a private S3 bucket stores releases and daily PostgreSQL backups. This is a development/demo topology and has a single point of failure.

RDS, a load balancer, private application subnets/NAT, and ECS or k3s remain explicit scaling decisions. They are added only after resource baselines, recovery objectives, or availability requirements justify them.

Later operations work adds OpenTelemetry propagation, metrics, structured logs, SLOs, and incident runbooks.
