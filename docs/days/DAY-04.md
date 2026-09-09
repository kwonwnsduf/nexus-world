# Day 4 — Local JWT authentication and RBAC

## Goal

Separate public and protected APIs without Cognito. Core API owns local accounts, verifies BCrypt passwords, issues short-lived JWT access tokens, and enforces role-based access control.

## Completed

- Added stateless Spring Security authentication with a custom `OncePerRequestFilter`.
- Added Flyway V2 tables for `users` and `user_roles` with status and role constraints.
- Added `POST /api/v1/auth/login` and protected `GET /api/v1/auth/me` contracts.
- Added `POST /api/v1/auth/refresh` with one-time refresh-token rotation and `POST /api/v1/auth/logout`.
- Added BCrypt cost-12 password hashing and constant-work handling for unknown usernames.
- Added a JJWT-based `JwtTokenProvider` for HS256 issuance and parsing with issuer, audience, subject, expiry, token ID, username, and roles.
- Added signature, issuer, audience, and expiry validation.
- Added `CustomUserDetails`, `CustomUserDetailsService`, and `DaoAuthenticationProvider` for the familiar username/password authentication flow.
- Added a JPA `UserAccount` entity and `UserRepository extends JpaRepository`; Hibernate validates, but never creates, the Flyway-owned schema.
- Added JPA refresh-session and access-token blacklist repositories backed by Flyway V3.
- Refresh tokens are stored only as SHA-256 hashes. Reuse detection revokes every token in the same family.
- Logout revokes the refresh family and blacklists the current access-token `jti` until it expires.
- Added hourly cleanup of expired refresh-token and blacklist records.
- Added RBAC roles `VIEWER`, `ANALYST`, `OPERATOR`, and `ADMIN`.
- Kept health, info, platform status, and login public; protected other v1 routes and restricted admin routes.
- Added an idempotent, opt-in bootstrap administrator for local Compose use.
- Added real PostgreSQL security integration tests for login, authenticated identity, 401, 403, and public health.

## Local credentials

Compose defaults are intentionally local-only:

```text
username: admin
password: nexus-world-local-admin
```

Override them through `.env`. The standalone application does not create an administrator unless `BOOTSTRAP_ADMIN_ENABLED=true`.

## Production requirements

- Generate and inject a unique `JWT_SECRET_BASE64` containing at least 32 random bytes.
- Disable the bootstrap administrator after provisioning real accounts.
- Store credentials and signing material in a managed secret store, never in Git or image layers.
- Add login throttling, password reset, refresh-token rotation, and key rotation before public production use.

## Deferred

Self-service registration, MFA, password recovery, login throttling, account administration APIs, quotas, and external identity federation are outside Day 4.
