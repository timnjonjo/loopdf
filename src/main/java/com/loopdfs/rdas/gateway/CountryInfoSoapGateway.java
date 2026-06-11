// gateway/CountryInfoSoapGateway.java
package com.loopdfs.rdas.gateway;

import com.loopdfs.rdas.domain.Continent;
import com.loopdfs.rdas.domain.Currency;
import com.loopdfs.rdas.domain.Language;

import java.util.List;
import java.util.Map;

/**
 * Anti-corruption layer: all SOAP interactions are hidden behind this interface.
 * Controllers and services depend only on this contract, never on generated stubs.
 */
public interface CountryInfoSoapGateway {

    /** Returns a map of ISO country code → continent code. */
    Map<String, String> fetchCountriesByContinent();

    /** Returns capital city for the given ISO country code. */
    String fetchCapitalCity(String isoCode);

    /** Returns currency ISO code for the given ISO country code. */
    String fetchCountryCurrencyIso(String isoCode);

    /** Returns human-readable currency name for a currency ISO code. */
    String fetchCurrencyName(String currencyIsoCode);

    /** Returns international phone code for an ISO country code. */
    String fetchPhoneCode(String isoCode);

    /** Returns the country's official name. */
    String fetchCountryName(String isoCode);

    /** Returns flag URL for the given ISO country code. */
    String fetchFlagUrl(String isoCode);

    /** Returns all available continents. */
    List<Continent> fetchContinents();

    /** Returns all available currencies. */
    List<Currency> fetchCurrencies();

    /** Returns all available languages. */
    List<Language> fetchLanguages();

    /** Returns language ISO code associated with the given country ISO code. */
    String fetchLanguageIso(String countryIsoCode);

    /** Returns language name for a language ISO code. */
    String fetchLanguageName(String languageIsoCode);
}