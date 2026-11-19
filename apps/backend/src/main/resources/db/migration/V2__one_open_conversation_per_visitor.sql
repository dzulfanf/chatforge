-- A visitor can have at most one open conversation at a time. Enforced in the database so that
-- concurrent requests cannot create duplicates.
CREATE UNIQUE INDEX uq_conversations_one_open_per_visitor
    ON conversations (visitor_id)
    WHERE status = 'OPEN';
