# Security and privacy baseline v0.2

- Never ingest real personal information for the civilization model.
- Never connect synthetic identifiers to external person identifiers.
- Store no secrets in Git; `.env.example` contains local-only placeholders.
- Web receives no database credentials.
- Public household and population APIs are aggregate-only with small-cell suppression.
- High-impact policy actions require future human approval before execution.
- Agent tools will be allow-listed, scoped, budgeted, timed out, and audited.
- Logs and traces must exclude credentials, document contents marked sensitive, and row-level household state.

## Day 4 local authentication

- Core API owns local user credentials; Cognito and other external identity providers are not used.
- Passwords are stored only as BCrypt hashes with cost 12. Login failures return the same message for unknown users and incorrect passwords.
- Core API issues short-lived HS256 JWT access tokens containing `iss`, `aud`, `sub`, `iat`, `exp`, `jti`, `username`, and `roles` claims.
- JWT signature, issuer, audience, and time claims are validated on every protected request. Server sessions are disabled.
- Refresh JWTs are stored only as SHA-256 hashes, rotated on every refresh, and grouped into token families. Reuse of an old refresh token revokes the entire family.
- Logout revokes the refresh-token family and stores the current access-token `jti` in a blacklist until expiration. Expired refresh and blacklist rows are deleted hourly.
- Roles are allow-listed as `VIEWER`, `ANALYST`, `OPERATOR`, and `ADMIN`. `/api/v1/admin/**` requires `ADMIN`; other non-public v1 APIs require authentication.
- Only health, info, platform status, and login are public. Authentication errors use 401; insufficient roles use 403.
- The Compose-only bootstrap administrator is for local development. Production must disable bootstrap credentials and inject a unique Base64-encoded secret of at least 32 bytes through a secret manager.
- Password reset, MFA, login rate limiting, and automatic account lockout remain future hardening work.
