
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES app_users(id)
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_family_id
    ON refresh_tokens(family_id);
