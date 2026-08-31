CREATE TABLE acl_policies (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    definition JSONB NOT NULL,
    CONSTRAINT uk_acl_policies_organization_name UNIQUE (organization_id, name),
    CONSTRAINT ck_acl_policies_kind CHECK (kind IN ('acl/request', 'acl/response')),
    CONSTRAINT ck_acl_policies_name_not_blank CHECK (btrim(name) <> '')
);
CREATE INDEX ix_acl_policies_organization_id ON acl_policies(organization_id);
