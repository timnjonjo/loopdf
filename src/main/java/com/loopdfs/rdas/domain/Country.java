package com.loopdfs.rdas.domain;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable aggregate root for a country,
 * assembled from multiple SOAP operations during bootstrap.
 */
@Value
@Builder
public class Country {

    /** ISO 3166-1 alpha-2 code, e.g. "KE". */
    String isoCode;

    /** Official English name, e.g. "Kenya". */
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