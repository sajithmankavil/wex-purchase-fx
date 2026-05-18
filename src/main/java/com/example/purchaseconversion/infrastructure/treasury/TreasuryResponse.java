package com.example.purchaseconversion.infrastructure.treasury;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors the U.S. Treasury Fiscal Data API
 * {@code /accounting/od/rates_of_exchange} response shape (the subset we use).
 * Unknown fields are ignored so Treasury can add fields without breaking parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TreasuryResponse(List<Row> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Row(
            @JsonProperty("country_currency_desc") String countryCurrencyDesc,
            @JsonProperty("record_date") String recordDate,
            @JsonProperty("effective_date") String effectiveDate,
            @JsonProperty("exchange_rate") String exchangeRate) {}
}
