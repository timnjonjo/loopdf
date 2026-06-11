// dto/CountryResponse.java
package com.loopdfs.rdas.dtos;

import lombok.Builder;
import lombok.Value;

/** Lightweight summary used in list / search results. */
@Value
@Builder
public class CountryResponse {
    String isoCode;
    String name;
    String continentCode;
    String currencyIsoCode;
    String currencyName;
    String languageName;
    String flagUrl;
}