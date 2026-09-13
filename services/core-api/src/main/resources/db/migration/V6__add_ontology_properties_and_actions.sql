CREATE TABLE ontology_property_types (
    entity_type VARCHAR(48) NOT NULL REFERENCES ontology_entity_types(code) ON DELETE RESTRICT,
    code VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    data_type VARCHAR(16) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    unit VARCHAR(80),
    classification VARCHAR(120),
    PRIMARY KEY (entity_type, code),
    CONSTRAINT ontology_property_code_not_blank CHECK (length(trim(code)) > 0),
    CONSTRAINT ontology_property_name_not_blank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT ontology_property_data_type_valid CHECK (
        data_type IN ('STRING','INTEGER','NUMBER','BOOLEAN','DATE','DATE_TIME','UUID','URI','OBJECT','ARRAY')
    )
);

CREATE TABLE ontology_action_types (
    code VARCHAR(64) PRIMARY KEY,
    actor_type VARCHAR(48) NOT NULL REFERENCES ontology_entity_types(code) ON DELETE RESTRICT,
    target_type VARCHAR(48) NOT NULL REFERENCES ontology_entity_types(code) ON DELETE RESTRICT,
    description TEXT NOT NULL,
    parameters_schema JSONB NOT NULL DEFAULT '{"type":"object"}'::jsonb,
    CONSTRAINT ontology_action_code_not_blank CHECK (length(trim(code)) > 0),
    CONSTRAINT ontology_action_parameters_object CHECK (jsonb_typeof(parameters_schema) = 'object')
);

INSERT INTO ontology_property_types
    (entity_type, code, display_name, description, data_type, required, unit, classification)
VALUES
('COUNTRY','isoCode','ISO country code','ISO 3166-1 alpha-2 country code','STRING',true,NULL,'ISO_3166_1_ALPHA_2'),
('REGION','countryCode','Country code','Parent country ISO code','STRING',true,NULL,'ISO_3166_1_ALPHA_2'),
('PORT','unLocode','UN/LOCODE','United Nations location code','STRING',true,NULL,'UN_LOCODE'),
('INDUSTRY','classificationCode','Industry code','Industry classification code','STRING',true,NULL,'ISIC'),
('COMPANY','jurisdiction','Jurisdiction','Company registration jurisdiction','STRING',true,NULL,'ISO_3166_1_ALPHA_2'),
('COMPANY','industryCode','Industry code','Primary industry classification code','STRING',true,NULL,'ISIC'),
('FACILITY','facilityKind','Facility kind','Production, storage, or distribution facility kind','STRING',true,NULL,NULL),
('FACILITY','regionCode','Region code','Region containing the facility','STRING',true,NULL,NULL),
('MATERIAL','unit','Measurement unit','Canonical quantity unit','STRING',true,NULL,'UCUM'),
('COMPONENT','unit','Measurement unit','Canonical quantity unit','STRING',true,NULL,'UCUM'),
('PRODUCT','unit','Measurement unit','Canonical quantity unit','STRING',true,NULL,'UCUM'),
('GOVERNMENT','jurisdictionCode','Jurisdiction code','Governed jurisdiction code','STRING',true,NULL,NULL),
('GOVERNMENT','level','Government level','National, regional, or local level','STRING',true,NULL,NULL),
('POLICY','policyKind','Policy kind','Tax, welfare, industrial, or regulatory category','STRING',true,NULL,NULL),
('POLICY','version','Policy version','Immutable policy rule version','STRING',true,NULL,NULL),
('BANK','jurisdiction','Jurisdiction','Bank regulatory jurisdiction','STRING',true,NULL,'ISO_3166_1_ALPHA_2'),
('LABOR_MARKET','regionCode','Region code','Labor-market region','STRING',true,NULL,NULL),
('LABOR_MARKET','baseYear','Base year','Statistical baseline year','INTEGER',true,'year',NULL),
('DEMOGRAPHIC_COHORT','populationModelId','Population model ID','Synthetic population model version identifier','STRING',true,NULL,NULL),
('DEMOGRAPHIC_COHORT','baseYear','Base year','Synthetic population baseline year','INTEGER',true,'year',NULL),
('DEMOGRAPHIC_COHORT','weight','Statistical weight','Represented population count','NUMBER',true,'persons',NULL),
('HOUSEHOLD_ARCHETYPE','populationModelId','Population model ID','Synthetic population model version identifier','STRING',true,NULL,NULL),
('HOUSEHOLD_ARCHETYPE','baseYear','Base year','Synthetic household baseline year','INTEGER',true,'year',NULL),
('HOUSEHOLD_ARCHETYPE','weight','Statistical weight','Represented household count','NUMBER',true,'households',NULL),
('OCCUPATION','classificationCode','Occupation code','Occupation classification code','STRING',true,NULL,'ISCO'),
('SKILL','classificationCode','Skill code','Optional skill classification code','STRING',false,NULL,NULL),
('WELFARE_BENEFIT','programCode','Program code','Public benefit program identifier','STRING',true,NULL,NULL),
('WELFARE_BENEFIT','version','Program version','Immutable eligibility and benefit-rule version','STRING',true,NULL,NULL),
('HOUSING_MARKET','regionCode','Region code','Housing-market region','STRING',true,NULL,NULL),
('HOUSING_MARKET','baseYear','Base year','Statistical baseline year','INTEGER',true,'year',NULL),
('SOCIAL_INDICATOR','metricCode','Metric code','Aggregate social metric identifier','STRING',true,NULL,NULL),
('SOCIAL_INDICATOR','unit','Measurement unit','Metric reporting unit','STRING',true,NULL,'UCUM'),
('CRISIS_EVENT','eventKind','Event kind','Crisis or shock classification','STRING',true,NULL,NULL),
('CRISIS_EVENT','startedAt','Start time','Time at which the crisis began','DATE_TIME',true,NULL,'ISO_8601');

INSERT INTO ontology_action_types
    (code, actor_type, target_type, description, parameters_schema)
VALUES
('REDUCE_PRODUCTION','COMPANY','FACILITY','Propose a bounded production reduction at a facility',
 '{"type":"object","required":["reductionRatio"],"additionalProperties":false,"properties":{"reductionRatio":{"type":"number","minimum":0,"maximum":1},"reason":{"type":"string"}}}'),
('ENACT_POLICY','GOVERNMENT','POLICY','Propose enactment of a versioned policy',
 '{"type":"object","required":["effectiveFrom"],"additionalProperties":false,"properties":{"effectiveFrom":{"type":"string","format":"date-time"},"reason":{"type":"string"}}}'),
('ADJUST_CREDIT','BANK','COMPANY','Propose an adjustment to company credit conditions',
 '{"type":"object","required":["creditLimitDelta"],"additionalProperties":false,"properties":{"creditLimitDelta":{"type":"number"},"interestRateDelta":{"type":"number"},"reason":{"type":"string"}}}');

CREATE INDEX ontology_property_types_entity_idx ON ontology_property_types(entity_type);
CREATE INDEX ontology_action_types_actor_target_idx ON ontology_action_types(actor_type, target_type);
