# Day 10: UN Comtrade ingestion

Status: Complete

The UN Comtrade adapter uses the subscribed data endpoint, not the truncated preview endpoint. Reporter/partner M49,
period, flow, commodity, classification edition, quantity unit, trade value currency, and source-native dimensions are
preserved in `TRADE_FLOW`. Requests are deliberately bounded and credential-redacted.
