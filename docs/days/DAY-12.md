# Day 12: Location and classification ingestion

Status: Complete

Separate UN/LOCODE, NGA WPI, HS and ISIC adapters consume official release files. ZIP traversal is rejected, quoted CSV
is parsed without losing delimiters, and every location/classification record retains its code system and release
version. This day only prepares relational canonical records; it does not perform entity resolution or write Neo4j.
