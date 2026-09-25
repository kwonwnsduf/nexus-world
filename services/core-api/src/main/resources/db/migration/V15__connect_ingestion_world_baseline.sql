UPDATE ontology_entity_types
SET required_attributes = '["countryCode","codeScheme"]'::jsonb
WHERE code = 'COUNTRY';

UPDATE ontology_property_types
SET required = FALSE
WHERE entity_type = 'COUNTRY' AND code = 'isoCode';

INSERT INTO ontology_property_types(
    entity_type, code, display_name, description, data_type, required, unit, classification
) VALUES
('COUNTRY','countryCode','Country code','Code from the declared source classification','STRING',true,NULL,NULL),
('COUNTRY','codeScheme','Country code scheme','ISO or source classification used by countryCode','STRING',true,NULL,NULL)
ON CONFLICT (entity_type, code) DO NOTHING;

INSERT INTO ontology_relationship_types(
    code, source_type, target_type, description, temporal
) VALUES
('TRADE_FLOW','COUNTRY','COUNTRY','Observed bilateral trade flow with commodity-level measurements',true)
ON CONFLICT (code, source_type, target_type) DO NOTHING;
