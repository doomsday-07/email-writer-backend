CREATE TABLE gmail_token (
    user_id        VARCHAR(128) PRIMARY KEY,
    access_token   TEXT         NOT NULL,
    refresh_token  TEXT         NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    scopes         TEXT         NOT NULL,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gmail_token_expires_at ON gmail_token (expires_at);
