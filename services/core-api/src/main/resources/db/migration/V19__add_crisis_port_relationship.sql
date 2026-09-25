INSERT INTO ontology_relationship_types(code, source_type, target_type, description, temporal)
VALUES ('AFFECTS','CRISIS_EVENT','PORT',
        'A geolocated crisis is within the configured proximity radius of a port',true)
ON CONFLICT (code, source_type, target_type) DO NOTHING;
