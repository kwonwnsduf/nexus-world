CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    normalized_username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    CONSTRAINT users_username_not_blank CHECK (length(trim(username)) > 0),
    CONSTRAINT users_normalized_username_not_blank CHECK (length(trim(normalized_username)) > 0),
    CONSTRAINT users_normalized_username_lowercase CHECK (normalized_username = lower(normalized_username)),
    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT users_normalized_username_unique UNIQUE (normalized_username)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role),
    CONSTRAINT user_roles_role_valid CHECK (role IN ('VIEWER', 'ANALYST', 'OPERATOR', 'ADMIN'))
);

CREATE INDEX users_status_idx ON users(status);
