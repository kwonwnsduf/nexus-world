# Day 8: SEC ingestion

Status: Complete

The Core API retrieves SEC EDGAR company submissions with a policy-compliant identifying `User-Agent`, an 8 requests/s
client-side ceiling below SEC's published maximum, bounded retry/timeout handling, exact raw-byte retention, and
canonical `SEC_FILING` rows. CIK is normalized to ten digits and filing accession numbers form stable natural keys.

Fixture tests are deterministic. Network checks live in the separately gated `live` test suite.
