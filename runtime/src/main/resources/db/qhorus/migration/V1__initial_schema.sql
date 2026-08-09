-- Qhorus consolidated schema — final state after V1-V41 + V2000-V2003 + slack-channel V23-V27.
-- Table order respects FK dependencies.

-- -------------------------------------------------------------------------
-- Space (hierarchy for channel grouping)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS space (
    id              UUID         NOT NULL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    parent_space_id UUID,
    tenancy_id      VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_space_parent FOREIGN KEY (parent_space_id) REFERENCES space(id),
    CONSTRAINT uq_space_name_parent_tenancy UNIQUE (tenancy_id, parent_space_id, name)
);

CREATE INDEX IF NOT EXISTS idx_space_parent ON space(parent_space_id);
CREATE INDEX IF NOT EXISTS idx_space_tenancy ON space(tenancy_id);

-- -------------------------------------------------------------------------
-- Channel
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS channel (
    id                     UUID         NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    description            VARCHAR(1000),
    semantic               VARCHAR(50)  NOT NULL,
    barrier_contributors   TEXT,
    paused                 BOOLEAN      NOT NULL DEFAULT FALSE,
    allowed_writers        TEXT,
    admin_instances        TEXT,
    rate_limit_per_channel INTEGER,
    rate_limit_per_instance INTEGER,
    allowed_types          TEXT,
    auto_created           BOOLEAN      NOT NULL DEFAULT FALSE,
    denied_types           TEXT,
    tenancy_id             VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    space_id               UUID,
    reviewer_instances     TEXT,
    protocols              TEXT,
    protocol_participants  TEXT,
    track_delivery         BOOLEAN,
    created_at             TIMESTAMP    NOT NULL,
    last_activity_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_channel PRIMARY KEY (id),
    CONSTRAINT uq_channel_name_tenancy UNIQUE (tenancy_id, name),
    CONSTRAINT chk_channel_name_slug
        CHECK (REGEXP_LIKE(name, '^[a-z][a-z0-9]*(-[a-z0-9]+)*(/[a-z][a-z0-9]*(-[a-z0-9]+)*)*$')
               AND LENGTH(name) <= 200),
    CONSTRAINT fk_channel_space FOREIGN KEY (space_id) REFERENCES space(id)
);

CREATE INDEX IF NOT EXISTS idx_channel_space ON channel(space_id);

-- -------------------------------------------------------------------------
-- Instance (agent presence registry)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS instance (
    id                  UUID         NOT NULL,
    instance_id         VARCHAR(255) NOT NULL,
    description         VARCHAR(1000),
    status              VARCHAR(50)  NOT NULL DEFAULT 'online',
    claudony_session_id VARCHAR(255),
    session_token       VARCHAR(255),
    read_only           BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen           TIMESTAMP    NOT NULL,
    registered_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_instance PRIMARY KEY (id),
    CONSTRAINT uq_instance_instance_id UNIQUE (instance_id)
);

-- -------------------------------------------------------------------------
-- Capability (capability tags per instance)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS capability (
    id          UUID         NOT NULL,
    instance_id UUID         NOT NULL,
    tag         VARCHAR(255) NOT NULL,
    CONSTRAINT pk_capability PRIMARY KEY (id),
    CONSTRAINT fk_capability_instance FOREIGN KEY (instance_id) REFERENCES instance(id)
);

-- -------------------------------------------------------------------------
-- Message (sequence PK preserves ordering)
-- -------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS message_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS message (
    id              BIGINT       NOT NULL,
    channel_id      UUID         NOT NULL,
    sender          VARCHAR(255) NOT NULL,
    message_type    VARCHAR(50)  NOT NULL,
    content         TEXT,
    correlation_id  VARCHAR(255),
    in_reply_to     BIGINT,
    reply_count     INT          NOT NULL DEFAULT 0,
    artefact_refs   TEXT,
    target          VARCHAR(255),
    actor_type      VARCHAR(10)  NOT NULL DEFAULT 'HUMAN',
    deadline        TIMESTAMP,
    acknowledged_at TIMESTAMP,
    commitment_id   UUID,
    tenancy_id      VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    version         INT          NOT NULL DEFAULT 0,
    topic           VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_message PRIMARY KEY (id),
    CONSTRAINT fk_message_channel FOREIGN KEY (channel_id) REFERENCES channel(id),
    CONSTRAINT fk_message_reply FOREIGN KEY (in_reply_to) REFERENCES message(id)
);

CREATE INDEX IF NOT EXISTS idx_message_channel_id ON message (channel_id, id);

-- -------------------------------------------------------------------------
-- Shared Data (artefact store)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shared_data (
    id          UUID         NOT NULL,
    data_key    VARCHAR(255) NOT NULL,
    content     TEXT,
    created_by  VARCHAR(255),
    description VARCHAR(1000),
    complete    BOOLEAN      NOT NULL DEFAULT TRUE,
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_shared_data PRIMARY KEY (id),
    CONSTRAINT uq_shared_data_key UNIQUE (data_key)
);

-- -------------------------------------------------------------------------
-- Artefact Claim (claim/release lifecycle for GC)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS artefact_claim (
    id          UUID      NOT NULL,
    artefact_id UUID      NOT NULL,
    instance_id UUID      NOT NULL,
    claimed_at  TIMESTAMP NOT NULL,
    CONSTRAINT pk_artefact_claim PRIMARY KEY (id),
    CONSTRAINT fk_artefact_claim_data FOREIGN KEY (artefact_id) REFERENCES shared_data(id),
    CONSTRAINT fk_artefact_claim_instance FOREIGN KEY (instance_id) REFERENCES instance(id)
);

-- -------------------------------------------------------------------------
-- Watchdog (condition-based alerting)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS watchdog (
    id                   UUID         NOT NULL,
    condition_type       VARCHAR(50)  NOT NULL,
    target_name          VARCHAR(255) NOT NULL,
    threshold_seconds    INT,
    threshold_count      INT,
    notification_channel VARCHAR(255) NOT NULL,
    created_by           VARCHAR(255),
    similarity_pct       INTEGER,
    tenancy_id           VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at           TIMESTAMP    NOT NULL,
    last_fired_at        TIMESTAMP,
    CONSTRAINT pk_watchdog PRIMARY KEY (id)
);

-- -------------------------------------------------------------------------
-- Commitment (obligation lifecycle for QUERY and COMMAND messages)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS commitment (
    id                   UUID         NOT NULL,
    correlation_id       VARCHAR(255) NOT NULL,
    channel_id           UUID         NOT NULL,
    message_type         VARCHAR(50)  NOT NULL,
    requester            VARCHAR(255) NOT NULL,
    obligor              VARCHAR(255),
    state                VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    expires_at           TIMESTAMP,
    acknowledged_at      TIMESTAMP,
    resolved_at          TIMESTAMP,
    delegated_to         VARCHAR(255),
    parent_commitment_id UUID,
    tenancy_id           VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at           TIMESTAMP    NOT NULL,
    CONSTRAINT pk_commitment PRIMARY KEY (id),
    CONSTRAINT fk_commitment_channel FOREIGN KEY (channel_id) REFERENCES channel(id),
    CONSTRAINT fk_commitment_parent FOREIGN KEY (parent_commitment_id) REFERENCES commitment(id)
);

CREATE INDEX IF NOT EXISTS idx_commitment_correlation_id ON commitment (correlation_id);
CREATE INDEX IF NOT EXISTS idx_commitment_channel_id ON commitment (channel_id);
CREATE INDEX IF NOT EXISTS idx_commitment_state_expires ON commitment (state, expires_at);
CREATE INDEX IF NOT EXISTS idx_commitment_obligor ON commitment (obligor);

-- -------------------------------------------------------------------------
-- Channel Connector Binding (external connector routing)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS channel_connector_binding (
    channel_id            UUID         NOT NULL,
    inbound_connector_id  VARCHAR(64)  NOT NULL,
    external_key          VARCHAR(255) NOT NULL,
    outbound_connector_id VARCHAR(64)  NOT NULL,
    outbound_destination  VARCHAR(512) NOT NULL,
    CONSTRAINT pk_channel_connector_binding PRIMARY KEY (channel_id),
    CONSTRAINT fk_binding_channel FOREIGN KEY (channel_id) REFERENCES channel(id),
    CONSTRAINT uq_binding_key UNIQUE (inbound_connector_id, external_key)
);

-- -------------------------------------------------------------------------
-- Delivery Cursor (per-channel, per-backend delivery tracking)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS delivery_cursor (
    id                     UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    channel_id             UUID         NOT NULL,
    backend_id             VARCHAR(255) NOT NULL,
    last_delivered_id      BIGINT,
    last_delivered_version INT          NOT NULL DEFAULT 0,
    updated_at             TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_delivery_cursor_channel_backend UNIQUE (channel_id, backend_id),
    CONSTRAINT fk_delivery_cursor_channel FOREIGN KEY (channel_id) REFERENCES channel(id) ON DELETE CASCADE
);

-- -------------------------------------------------------------------------
-- Topic (per-channel named topics)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS topic (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    channel_id  UUID         NOT NULL,
    name        VARCHAR(200) NOT NULL,
    resolved    BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(255),
    tenancy_id  VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_topic_channel_name_tenancy UNIQUE (channel_id, name, tenancy_id),
    CONSTRAINT fk_topic_channel FOREIGN KEY (channel_id) REFERENCES channel(id)
);

-- -------------------------------------------------------------------------
-- Reaction (per-message emoji reactions)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reaction (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    message_id BIGINT       NOT NULL,
    emoji      VARCHAR(100) NOT NULL,
    actor_id   VARCHAR(255) NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_reaction_message_emoji_actor UNIQUE (message_id, emoji, actor_id),
    CONSTRAINT fk_reaction_message FOREIGN KEY (message_id) REFERENCES message(id)
);

CREATE INDEX IF NOT EXISTS idx_reaction_message_id ON reaction(message_id);

-- -------------------------------------------------------------------------
-- Channel Membership
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS channel_membership (
    id                       BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    channel_id               UUID         NOT NULL,
    member_id                VARCHAR(255) NOT NULL,
    member_role              VARCHAR(50)  NOT NULL,
    tenancy_id               VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    joined_at                TIMESTAMP    NOT NULL,
    last_read_message_id     BIGINT,
    last_delivered_message_id BIGINT,
    CONSTRAINT uq_membership_channel_member UNIQUE (channel_id, member_id),
    CONSTRAINT fk_membership_channel FOREIGN KEY (channel_id) REFERENCES channel(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_membership_member_id ON channel_membership(member_id);

-- -------------------------------------------------------------------------
-- Webhook Registration
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS webhook_registration (
    id         UUID PRIMARY KEY,
    channel_id UUID,
    url        VARCHAR(2048) NOT NULL,
    secret_ref VARCHAR(255),
    headers    TEXT,
    tenancy_id VARCHAR(255)  NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at TIMESTAMP     NOT NULL,
    CONSTRAINT uq_webhook_url_channel_tenant UNIQUE (url, channel_id, tenancy_id),
    CONSTRAINT fk_webhook_channel FOREIGN KEY (channel_id) REFERENCES channel(id)
);

-- -------------------------------------------------------------------------
-- Channel Summary
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS channel_summary (
    id                      UUID PRIMARY KEY,
    channel_id              UUID         NOT NULL UNIQUE REFERENCES channel(id) ON DELETE CASCADE,
    content                 TEXT,
    updated_at              TIMESTAMP,
    updated_by              VARCHAR(255),
    last_updated_message_id BIGINT,
    update_after_messages   INTEGER,
    update_after_seconds    INTEGER,
    annotations             TEXT,
    tenancy_id              VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce'
);

-- -------------------------------------------------------------------------
-- Slack Bot Binding (slack-channel module)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS slack_bot_binding (
    channel_id       UUID         NOT NULL,
    slack_channel_id VARCHAR(32)  NOT NULL,
    workspace_id     VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_slack_bot_binding PRIMARY KEY (channel_id),
    CONSTRAINT fk_slack_binding_channel FOREIGN KEY (channel_id) REFERENCES channel(id),
    CONSTRAINT uq_slack_bot_slack_id UNIQUE (slack_channel_id)
);

-- -------------------------------------------------------------------------
-- Slack Thread Cache (slack-channel module)
-- correlation_id is VARCHAR(255) — originally UUID, widened in V27.
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS slack_thread_cache (
    channel_id     UUID         NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    thread_ts      VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT pk_slack_thread_cache PRIMARY KEY (channel_id, correlation_id),
    CONSTRAINT uq_slack_thread_ts UNIQUE (channel_id, thread_ts)
);

-- -------------------------------------------------------------------------
-- Message Ledger Entry (cross-project: FK to ledger_entry from casehub-ledger)
-- CONSTRAINT fk_message_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry (id)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_ledger_entry (
    id                 UUID         NOT NULL,
    channel_id         UUID         NOT NULL,
    message_id         BIGINT       NOT NULL,
    message_type       VARCHAR(50)  NOT NULL,
    target             VARCHAR(255),
    content            TEXT,
    correlation_id     VARCHAR(255),
    commitment_id      UUID,
    tool_name          VARCHAR(255),
    duration_ms        BIGINT,
    token_count        BIGINT,
    context_refs       TEXT,
    source_entity      TEXT,
    topic              VARCHAR(200),
    context_window_pct SMALLINT,
    CONSTRAINT pk_message_ledger_entry PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mle_channel ON message_ledger_entry (channel_id);
CREATE INDEX IF NOT EXISTS idx_mle_message_id ON message_ledger_entry (message_id);
CREATE INDEX IF NOT EXISTS idx_mle_correlation ON message_ledger_entry (correlation_id);

