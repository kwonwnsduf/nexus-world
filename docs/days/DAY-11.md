# Day 11: World Bank and USGS ingestion

Status: Complete

World Bank Indicators API v2 pagination produces versioned `MACRO_INDICATOR` observations with ISO alpha-3 geography,
period, unit and indicator code. USGS FDSN GeoJSON produces `EARTHQUAKE` observations with event time, magnitude, depth,
coordinates, network, type and API version. Neither public endpoint is required by health checks.
