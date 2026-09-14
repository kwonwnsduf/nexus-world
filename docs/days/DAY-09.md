# Day 9: OpenDART ingestion

Status: Complete

The dedicated OpenDART adapter reads its 40-character key only from `OPENDART_API_KEY`, paginates disclosure searches,
interprets OpenDART status codes, redacts the key from stored URIs, and preserves corporation, market, report, receipt,
and filing-date identifiers as `OPENDART_FILING` records.
