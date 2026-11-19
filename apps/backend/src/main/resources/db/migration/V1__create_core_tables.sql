CREATE TABLE agents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE visitors (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visitor_id        UUID        NOT NULL REFERENCES visitors (id),
    assigned_agent_id UUID        REFERENCES agents (id),
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT conversations_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_conversations_visitor ON conversations (visitor_id);
CREATE INDEX idx_conversations_status_updated ON conversations (status, updated_at DESC);

-- sender_id references either an agent or a visitor depending on sender_type,
-- so it has no foreign key; integrity is enforced in the application layer.
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversations (id),
    sender_type     VARCHAR(20) NOT NULL,
    sender_id       UUID        NOT NULL,
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT messages_sender_type_check CHECK (sender_type IN ('AGENT', 'VISITOR')),
    CONSTRAINT messages_content_not_blank CHECK (length(btrim(content)) > 0)
);

CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);
