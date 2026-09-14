# Day 13: Population and household target ingestion

Status: Complete

Separate UN WPP, ILOSTAT, KOSIS and OECD adapters ingest official bulk CSV/GZIP or SDMX/OpenAPI output. Population,
labour and household-economic observations retain geography scheme, period, unit, classification, data version and all
source dimensions needed for later calibration. KOSIS credentials remain environment-only.

Day 14 entity resolution and Neo4j loading are explicitly excluded.
