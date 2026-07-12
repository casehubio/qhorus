CREATE TABLE space (
    id          UUID         NOT NULL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    parent_space_id UUID,
    tenancy_id  VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_space_parent FOREIGN KEY (parent_space_id) REFERENCES space(id),
    CONSTRAINT uq_space_name_parent_tenancy UNIQUE (tenancy_id, parent_space_id, name)
);

CREATE INDEX idx_space_parent ON space(parent_space_id);
CREATE INDEX idx_space_tenancy ON space(tenancy_id);
