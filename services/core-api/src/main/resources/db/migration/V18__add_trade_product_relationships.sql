INSERT INTO ontology_entity_types(code, domain, display_name, description, required_attributes, aggregate_only)
VALUES ('TRANSPORT_MODE','GEOGRAPHY','Transport mode',
        'Source-classified mode used to move traded goods', '["modeCode"]'::jsonb,false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO ontology_property_types
    (entity_type, code, display_name, description, data_type, required, unit, classification)
VALUES ('TRANSPORT_MODE','modeCode','Mode code','Source-native mode-of-transport code',
        'STRING',true,NULL,NULL)
ON CONFLICT (entity_type, code) DO NOTHING;

INSERT INTO ontology_relationship_types(code, source_type, target_type, description, temporal)
VALUES
('PRODUCES','COUNTRY','PRODUCT','Country reports exports of a classified product',true),
('SUPPLIES','PRODUCT','COUNTRY','Classified product is supplied to an importing country',true),
('DEPENDS_ON','COUNTRY','PRODUCT','Importing country depends on a classified imported product',true),
('SHIPS_VIA','PRODUCT','TRANSPORT_MODE','Product is reported under a mode of transport',true)
ON CONFLICT (code, source_type, target_type) DO NOTHING;
