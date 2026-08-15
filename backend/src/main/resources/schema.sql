CREATE TABLE IF NOT EXISTS sessions (
    id                TEXT PRIMARY KEY,
    name              TEXT NOT NULL,
    cwd               TEXT NOT NULL,
    prompt            TEXT,
    model             TEXT,
    permission_mode   TEXT NOT NULL DEFAULT 'default',
    auto_approve      INTEGER NOT NULL DEFAULT 0,
    status            TEXT NOT NULL,
    origin            TEXT NOT NULL DEFAULT 'acc',
    claude_session_id TEXT,
    pid               INTEGER,
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL,
    ended_at          INTEGER,
    exit_code         INTEGER,
    result_text       TEXT,
    total_cost_usd    REAL,
    num_turns         INTEGER,
    duration_ms       INTEGER
);

CREATE INDEX IF NOT EXISTS idx_sessions_claude_id ON sessions (claude_session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_created  ON sessions (created_at DESC);

CREATE TABLE IF NOT EXISTS events (
    id          TEXT PRIMARY KEY,
    session_id  TEXT NOT NULL,
    seq         INTEGER NOT NULL,
    ts          INTEGER NOT NULL,
    type        TEXT NOT NULL,
    parent_id   TEXT,
    tool_use_id TEXT,
    tool_name   TEXT,
    title       TEXT,
    status      TEXT,
    duration_ms INTEGER,
    payload     TEXT
);

CREATE INDEX IF NOT EXISTS idx_events_session ON events (session_id, seq);
CREATE INDEX IF NOT EXISTS idx_events_tooluse ON events (session_id, tool_use_id);

CREATE TABLE IF NOT EXISTS approvals (
    id                TEXT PRIMARY KEY,
    session_id        TEXT,
    claude_session_id TEXT,
    hook_event        TEXT NOT NULL,
    tool_name         TEXT,
    tool_input        TEXT,
    risk              TEXT NOT NULL DEFAULT 'normal',
    status            TEXT NOT NULL,
    reason            TEXT,
    created_at        INTEGER NOT NULL,
    decided_at        INTEGER
);

CREATE INDEX IF NOT EXISTS idx_approvals_session ON approvals (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_approvals_status  ON approvals (status);
