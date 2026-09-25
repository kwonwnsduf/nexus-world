# Automatic ingestion request plan

This file is the checked-in request inventory for scheduled collection. It is deliberately
separate from credentials: API keys and the SEC contact user-agent remain environment values.
The scheduler accepts either the legacy single `parametersJson` value or a `requests` array.
Every object in `requests` becomes a separate audited ingestion run.

Do not collapse this inventory to one convenient query per API. Coverage must span entities,
periods, indicators, directions, and classifications. Bulk sources are the exception: one HTTP
request already downloads the complete official file, so breadth is expressed by the rows in the
file rather than repeated identical downloads.

## Coverage inventory

| Source | Requests to register before enabling production schedules | Refresh guidance |
|---|---|---|
| SEC | One request per tracked CIK; keep `includeCompanyFacts=true` so submissions and all company facts are collected. Seed at least the target peer group, suppliers, customers, and major competitors—not a single flagship company. | Daily, staggered if the tracked universe is large. |
| OpenDART | Rolling all-market disclosure windows plus corp-specific requests for every tracked Korean issuer. For each corp-specific request set `includeCompanyProfile=true`; this collects both `corp_name` and `corp_name_eng` for entity alias matching. Use smaller date windows and `maxPages` high enough to avoid silent truncation. | Daily; backfill month-by-month. |
| UN Comtrade | Cross product of reporter markets, monthly/annual periods, import/export flow, and key HS groups. Include `TOTAL` for reconciliation and detailed HS codes for propagation edges. | Monthly plus annual reconciliation. |
| World Bank | Cross product of target countries/regions and GDP, population, inflation, trade, manufacturing, employment, energy, and logistics indicators. | Monthly check; backfill long date ranges once. |
| USGS | Multiple adjacent time windows, including a broad lower-magnitude feed and a high-severity feed. Never request an unbounded history in one call. | Every 5–15 minutes. |
| UN/LOCODE | The complete official release. It already contains all locations; do not issue duplicate requests for individual ports. | Each official release. |
| WPI | The complete World Port Index file. It already contains all ports. | Monthly release check. |
| HS | Complete HS classification, retaining the requested classification version in provenance. | On classification release. |
| ISIC | Complete ISIC structure, retaining the requested classification version in provenance. | On classification release. |
| UN WPP | Complete demographic indicator bulk file; retain all countries, variants, ages/sexes available in the source rows. | On WPP release. |
| ILOSTAT | One request per required bulk dataset/indicator family: employment, unemployment, labour force, wages, hours, occupations, and sector employment. | Monthly/quarterly depending on dataset. |
| KOSIS | One request per approved `(userStatsId, orgId, tableId)` with complete available periods. Register population, employment, production, prices, trade, regional accounts, and industry tables. | Monthly; respect table-specific periodicity. |
| OECD | One request per SDMX dataflow and key slice. Cover national accounts, prices, labour, trade, industry, productivity, energy, and leading indicators rather than using only one flow. | Monthly/quarterly. |

## Schedule-ready example

The identifiers below are examples and must be replaced by the maintained target universe. The
shape is directly accepted in `INGESTION_SCHEDULE_SOURCES_JSON`.

```json
{
  "SEC": {
    "enabled": true,
    "cron": "0 15 2 * * *",
    "requests": [
      {"cik": "0000320193", "includeCompanyFacts": true},
      {"cik": "0000789019", "includeCompanyFacts": true},
      {"cik": "0001652044", "includeCompanyFacts": true},
      {"cik": "0000051143", "includeCompanyFacts": true}
    ]
  },
  "OPENDART": {
    "enabled": true,
    "cron": "0 45 2 * * *",
    "requests": [
      {"startDate": "20260901", "endDate": "20260930", "pageSize": 100, "maxPages": 100},
      {"corpCode": "00126380", "includeCompanyProfile": true, "startDate": "20260101", "endDate": "20260930", "pageSize": 100, "maxPages": 100},
      {"corpCode": "00164779", "includeCompanyProfile": true, "startDate": "20260101", "endDate": "20260930", "pageSize": 100, "maxPages": 100}
    ]
  },
  "UN_COMTRADE": {
    "enabled": true,
    "cron": "0 10 3 2 * *",
    "requests": [
      {"reporterCode": "410", "period": "2025", "partnerCode": "0", "flowCode": "X", "cmdCode": "TOTAL", "classification": "HS"},
      {"reporterCode": "410", "period": "2025", "partnerCode": "0", "flowCode": "M", "cmdCode": "TOTAL", "classification": "HS"},
      {"reporterCode": "842", "period": "2025", "partnerCode": "0", "flowCode": "X", "cmdCode": "TOTAL", "classification": "HS"},
      {"reporterCode": "156", "period": "2025", "partnerCode": "0", "flowCode": "X", "cmdCode": "TOTAL", "classification": "HS"},
      {"reporterCode": "392", "period": "2025", "partnerCode": "0", "flowCode": "X", "cmdCode": "8542", "classification": "HS"}
    ]
  },
  "WORLD_BANK": {
    "enabled": true,
    "cron": "0 20 4 3 * *",
    "requests": [
      {"country": "KOR", "indicator": "NY.GDP.MKTP.CD", "date": "2000:2026"},
      {"country": "KOR", "indicator": "NV.IND.MANF.CD", "date": "2000:2026"},
      {"country": "USA", "indicator": "NY.GDP.MKTP.CD", "date": "2000:2026"},
      {"country": "CHN", "indicator": "NE.TRD.GNFS.ZS", "date": "2000:2026"},
      {"country": "WLD", "indicator": "FP.CPI.TOTL.ZG", "date": "2000:2026"}
    ]
  },
  "USGS": {
    "enabled": true,
    "cron": "0 */10 * * * *",
    "requests": [
      {"startTime": "2026-09-24T00:00:00Z", "endTime": "2026-09-25T00:00:00Z", "minMagnitude": "4.5", "limit": "20000"},
      {"startTime": "2026-09-18T00:00:00Z", "endTime": "2026-09-25T00:00:00Z", "minMagnitude": "6.0", "limit": "20000"}
    ]
  },
  "ILOSTAT": {
    "enabled": true,
    "cron": "0 30 4 4 * *",
    "requests": [
      {"dataset": "EMP_TEMP_SEX_ECO_NB_A"},
      {"dataset": "UNE_DEAP_SEX_AGE_RT_A"},
      {"dataset": "EAR_4MTH_SEX_ECO_CUR_NB_A"}
    ]
  },
  "KOSIS": {
    "enabled": false,
    "cron": "0 40 4 5 * *",
    "requests": [
      {"userStatsId": "replace-me", "orgId": "101", "tableId": "DT_REPLACE_POPULATION", "periodicity": "M", "startPeriod": "202001", "endPeriod": "202612"},
      {"userStatsId": "replace-me", "orgId": "101", "tableId": "DT_REPLACE_EMPLOYMENT", "periodicity": "M", "startPeriod": "202001", "endPeriod": "202612"},
      {"userStatsId": "replace-me", "orgId": "101", "tableId": "DT_REPLACE_INDUSTRY", "periodicity": "M", "startPeriod": "202001", "endPeriod": "202612"}
    ]
  },
  "OECD": {
    "enabled": true,
    "cron": "0 50 4 6 * *",
    "requests": [
      {"flowRef": "DF_QNA", "key": "all", "startPeriod": "2015-Q1", "endPeriod": "2026-Q4"},
      {"flowRef": "DF_DP_LIVE", "key": "all", "startPeriod": "2015", "endPeriod": "2026"},
      {"flowRef": "DF_KEI", "key": "all", "startPeriod": "2015-01", "endPeriod": "2026-12"}
    ]
  },
  "UNLOCODE": {"enabled": true, "cron": "0 0 5 1 */3 *", "requests": [{"version": "2025-1"}]},
  "WPI": {"enabled": true, "cron": "0 10 5 1 * *", "requests": [{"version": "monthly"}]},
  "HS": {"enabled": true, "cron": "0 20 5 1 1 *", "requests": [{"version": "HS2022"}]},
  "ISIC": {"enabled": true, "cron": "0 30 5 1 1 *", "requests": [{"version": "ISIC4"}]},
  "UN_WPP": {"enabled": true, "cron": "0 40 5 1 1 *", "requests": [{"version": "WPP2024"}]}
}
```

Dates in rolling requests must be advanced by deployment automation; static example dates are
not intended to run forever. Before enabling a source, estimate request count and payload size,
confirm provider rate limits/terms, replace placeholders, and backfill in bounded batches.

## Entity resolution boundary

Collection preserves every source-native company identifier and every available Korean and English
name in normalized dimensions. World building stores those values in `entity_name_aliases` and
compares them within the same entity type and world version. Trusted identifiers remain authoritative;
a unique exact alias, or one unambiguous normalized-name similarity of at least 0.96, can attach a
new source identifier to the matched entity. Tied or weaker names never merge automatically.
