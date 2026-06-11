// dto/CountryDetailResponse.java
package com.loopdfs.rdas.dtos;

import lombok.Builder;
import lombok.Value;

/** Full detail payload returned by GET /countries/{isoCode}. */
@Value
@Builder
public class CountryDetailResponse {
    String isoCode;
    String name;
    String continentCode;
    String capitalCity;
    String currencyIsoCode;
    String currencyName;
    String internationalPhoneCode;
    String languageIsoCode;
    String languageName;
    String flagUrl;
}