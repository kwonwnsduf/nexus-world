INSERT INTO ontology_entity_types(code, domain, display_name, description, required_attributes, aggregate_only)
VALUES ('STATISTICAL_INDICATOR','SOCIETY','Statistical indicator',
        'A source-versioned aggregate indicator observation',
        '["metricCode","sourceSystem","unit"]'::jsonb,true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO ontology_property_types
    (entity_type, code, display_name, description, data_type, required, unit, classification)
VALUES
('STATISTICAL_INDICATOR','metricCode','Metric code','Source-native metric code','STRING',true,NULL,NULL),
('STATISTICAL_INDICATOR','sourceSystem','Source system','Originating statistical system','STRING',true,NULL,NULL),
('STATISTICAL_INDICATOR','unit','Unit','Source-native measurement unit','STRING',true,NULL,NULL),
('STATISTICAL_INDICATOR','value','Observed value','Latest observed aggregate value','NUMBER',false,NULL,NULL),
('STATISTICAL_INDICATOR','period','Observation period','Source observation period','STRING',false,NULL,NULL)
ON CONFLICT (entity_type, code) DO NOTHING;

INSERT INTO ontology_relationship_types(code, source_type, target_type, description, temporal)
VALUES
('HAS_INDICATOR','COUNTRY','STATISTICAL_INDICATOR','Country has a source-versioned aggregate indicator',true),
('HAS_INDICATOR','COMPANY','STATISTICAL_INDICATOR','Company has a source-versioned reported indicator',true),
('LOCATED_IN','PORT','COUNTRY','Port or UN/LOCODE location belongs to a country',true),
('PARENT_OF','PRODUCT','PRODUCT','Classification parent contains a child product code',false),
('PARENT_OF','INDUSTRY','INDUSTRY','Classification parent contains a child industry code',false)
ON CONFLICT (code, source_type, target_type) DO NOTHING;
