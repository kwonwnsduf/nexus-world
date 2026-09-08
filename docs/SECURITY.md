# Security and privacy baseline v0.1

- Never ingest real personal information for the civilization model.
- Never connect synthetic identifiers to external person identifiers.
- Store no secrets in Git; `.env.example` contains local-only placeholders.
- Web receives no database credentials.
- Public household and population APIs are aggregate-only with small-cell suppression.
- High-impact policy actions require future human approval before execution.
- Agent tools will be allow-listed, scoped, budgeted, timed out, and audited.
- Logs and traces must exclude credentials, document contents marked sensitive, and row-level household state.

