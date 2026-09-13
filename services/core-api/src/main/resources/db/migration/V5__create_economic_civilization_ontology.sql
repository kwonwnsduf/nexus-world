CREATE TABLE ontology_entity_types (
    code VARCHAR(48) PRIMARY KEY,
    domain VARCHAR(24) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    required_attributes JSONB NOT NULL DEFAULT '[]'::jsonb,
    aggregate_only BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ontology_entity_domain_valid CHECK (domain IN ('GEOGRAPHY','INDUSTRY','SOCIETY','GOVERNMENT','FINANCE','EVENT')),
    CONSTRAINT ontology_entity_required_array CHECK (jsonb_typeof(required_attributes) = 'array')
);

CREATE TABLE ontology_relationship_types (
    code VARCHAR(48) NOT NULL,
    source_type VARCHAR(48) NOT NULL REFERENCES ontology_entity_types(code) ON DELETE RESTRICT,
    target_type VARCHAR(48) NOT NULL REFERENCES ontology_entity_types(code) ON DELETE RESTRICT,
    description TEXT NOT NULL,
    temporal BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (code, source_type, target_type)
);

INSERT INTO ontology_entity_types(code, domain, display_name, description, required_attributes, aggregate_only) VALUES
('COUNTRY','GEOGRAPHY','Country','Sovereign statistical and policy jurisdiction','["isoCode"]',false),
('REGION','GEOGRAPHY','Region','Subnational economic and population region','["countryCode"]',false),
('PORT','GEOGRAPHY','Port','Maritime or inland logistics node','["unLocode"]',false),
('INDUSTRY','INDUSTRY','Industry','Versioned industrial classification','["classificationCode"]',false),
('COMPANY','INDUSTRY','Company','Legal or consolidated economic producer','["jurisdiction","industryCode"]',false),
('FACILITY','INDUSTRY','Facility','Physical production, storage, or distribution site','["facilityKind","regionCode"]',false),
('MATERIAL','INDUSTRY','Material','Raw or processed production input','["unit"]',false),
('COMPONENT','INDUSTRY','Component','Intermediate input used by products','["unit"]',false),
('PRODUCT','INDUSTRY','Product','Final or intermediate market output','["unit"]',false),
('GOVERNMENT','GOVERNMENT','Government','Public authority with a defined jurisdiction','["jurisdictionCode","level"]',false),
('POLICY','GOVERNMENT','Policy','Versioned tax, welfare, industrial, or regulatory policy','["policyKind","version"]',false),
('BANK','FINANCE','Bank','Regulated credit and deposit institution','["jurisdiction"]',false),
('LABOR_MARKET','SOCIETY','Labor market','Regional and industrial labor-market aggregate','["regionCode","baseYear"]',true),
('DEMOGRAPHIC_COHORT','SOCIETY','Demographic cohort','Weighted synthetic population cohort; never a real person','["populationModelId","baseYear","weight"]',true),
('HOUSEHOLD_ARCHETYPE','SOCIETY','Household archetype','Weighted synthetic household class; never a real household','["populationModelId","baseYear","weight"]',true),
('OCCUPATION','SOCIETY','Occupation','Versioned occupation classification','["classificationCode"]',false),
('SKILL','SOCIETY','Skill','Labor capability or qualification','[]',false),
('WELFARE_BENEFIT','SOCIETY','Welfare benefit','Versioned public transfer program','["programCode","version"]',false),
('HOUSING_MARKET','SOCIETY','Housing market','Regional aggregate housing market','["regionCode","baseYear"]',true),
('SOCIAL_INDICATOR','SOCIETY','Social indicator','Aggregate population outcome metric','["metricCode","unit"]',true),
('CRISIS_EVENT','EVENT','Crisis event','Time-bounded exogenous or endogenous shock','["eventKind","startedAt"]',false);

INSERT INTO ontology_relationship_types(code, source_type, target_type, description, temporal) VALUES
('PART_OF','REGION','COUNTRY','Region belongs to a country',false),
('LOCATED_IN','PORT','REGION','Port is located in a region',false),
('LOCATED_IN','FACILITY','REGION','Facility is located in a region',true),
('OPERATES','COMPANY','FACILITY','Company operates a facility',true),
('CLASSIFIED_AS','COMPANY','INDUSTRY','Company participates in an industry',true),
('PRODUCES','FACILITY','COMPONENT','Facility produces a component',true),
('PRODUCES','FACILITY','PRODUCT','Facility produces a product',true),
('REQUIRES','COMPONENT','MATERIAL','Component requires material input',true),
('REQUIRES','PRODUCT','COMPONENT','Product requires component input',true),
('SUPPLIES','COMPANY','COMPANY','Company supplies another company',true),
('GOVERNS','GOVERNMENT','COUNTRY','Government governs a country',true),
('GOVERNS','GOVERNMENT','REGION','Government governs a region',true),
('ENACTS','GOVERNMENT','POLICY','Government enacts a versioned policy',true),
('REGULATES','GOVERNMENT','INDUSTRY','Government regulates an industry',true),
('SUBSIDIZES','POLICY','INDUSTRY','Policy subsidizes an industry',true),
('PROVIDES','POLICY','WELFARE_BENEFIT','Policy provides a welfare benefit',true),
('EMPLOYS','COMPANY','DEMOGRAPHIC_COHORT','Company employs a weighted cohort',true),
('DEMANDS_SKILL','COMPANY','SKILL','Company demands a skill',true),
('HAS_OCCUPATION','DEMOGRAPHIC_COHORT','OCCUPATION','Cohort has an occupation distribution',true),
('HAS_SKILL','DEMOGRAPHIC_COHORT','SKILL','Cohort has a skill distribution',true),
('PARTICIPATES_IN','DEMOGRAPHIC_COHORT','LABOR_MARKET','Cohort participates in a labor market',true),
('MEMBER_PROFILE_OF','DEMOGRAPHIC_COHORT','HOUSEHOLD_ARCHETYPE','Cohort contributes members to a household archetype',true),
('CONSUMES','HOUSEHOLD_ARCHETYPE','PRODUCT','Household archetype consumes a product',true),
('SAVES_AT','HOUSEHOLD_ARCHETYPE','BANK','Household archetype saves at a bank',true),
('BORROWS_FROM','HOUSEHOLD_ARCHETYPE','BANK','Household archetype borrows from a bank',true),
('LENDS_TO','BANK','COMPANY','Bank lends to a company',true),
('SERVES','BANK','COUNTRY','Bank serves a country market',true),
('TRACKS','SOCIAL_INDICATOR','DEMOGRAPHIC_COHORT','Indicator tracks an aggregate cohort',true),
('DESCRIBES','LABOR_MARKET','REGION','Labor market describes a region',true),
('DESCRIBES','HOUSING_MARKET','REGION','Housing market describes a region',true),
('AFFECTS','CRISIS_EVENT','REGION','Crisis affects a region',true),
('AFFECTS','CRISIS_EVENT','FACILITY','Crisis affects a facility',true),
('AFFECTS','CRISIS_EVENT','LABOR_MARKET','Crisis affects a labor market',true),
('AFFECTS','CRISIS_EVENT','HOUSING_MARKET','Crisis affects a housing market',true);

CREATE TABLE world_graph_entities (
    id UUID PRIMARY KEY,
    world_version_id UUID NOT NULL REFERENCES world_versions(id) ON DELETE RESTRICT,
    entity_type VARCHAR(48) NOT NULL REFERENCES ontology_entity_types(code) ON DELETE RESTRICT,
    natural_key VARCHAR(200) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT world_graph_entity_key_not_blank CHECK (length(trim(natural_key)) > 0),
    CONSTRAINT world_graph_entity_name_not_blank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT world_graph_entity_attributes_object CHECK (jsonb_typeof(attributes) = 'object'),
    CONSTRAINT world_graph_entity_valid_period CHECK (valid_to IS NULL OR (valid_from IS NOT NULL AND valid_to >= valid_from)),
    CONSTRAINT world_graph_entity_natural_key_unique UNIQUE (world_version_id, natural_key),
    CONSTRAINT world_graph_entity_identity_unique UNIQUE (world_version_id, id, entity_type)
);

CREATE TABLE world_graph_relationships (
    id UUID PRIMARY KEY,
    world_version_id UUID NOT NULL REFERENCES world_versions(id) ON DELETE RESTRICT,
    relationship_type VARCHAR(48) NOT NULL,
    source_entity_id UUID NOT NULL,
    source_entity_type VARCHAR(48) NOT NULL,
    target_entity_id UUID NOT NULL,
    target_entity_type VARCHAR(48) NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT world_graph_relationship_definition_fk FOREIGN KEY (relationship_type, source_entity_type, target_entity_type)
        REFERENCES ontology_relationship_types(code, source_type, target_type) ON DELETE RESTRICT,
    CONSTRAINT world_graph_relationship_source_fk FOREIGN KEY (world_version_id, source_entity_id, source_entity_type)
        REFERENCES world_graph_entities(world_version_id, id, entity_type) ON DELETE RESTRICT,
    CONSTRAINT world_graph_relationship_target_fk FOREIGN KEY (world_version_id, target_entity_id, target_entity_type)
        REFERENCES world_graph_entities(world_version_id, id, entity_type) ON DELETE RESTRICT,
    CONSTRAINT world_graph_relationship_not_self CHECK (source_entity_id <> target_entity_id),
    CONSTRAINT world_graph_relationship_attributes_object CHECK (jsonb_typeof(attributes) = 'object'),
    CONSTRAINT world_graph_relationship_valid_period CHECK (valid_to IS NULL OR (valid_from IS NOT NULL AND valid_to >= valid_from))
);

CREATE INDEX world_graph_entities_version_type_idx ON world_graph_entities(world_version_id, entity_type);
CREATE INDEX world_graph_relationships_version_idx ON world_graph_relationships(world_version_id);
CREATE INDEX world_graph_relationships_source_idx ON world_graph_relationships(source_entity_id);
CREATE INDEX world_graph_relationships_target_idx ON world_graph_relationships(target_entity_id);
CREATE UNIQUE INDEX world_graph_relationship_unique_idx ON world_graph_relationships
    (world_version_id, relationship_type, source_entity_id, target_entity_id, COALESCE(valid_from, '-infinity'::timestamptz));
