// service/ReferenceDataStore.java
package com.loopdfs.rdas.services;

import com.loopdfs.rdas.domain.Continent;
import com.loopdfs.rdas.domain.Country;
import com.loopdfs.rdas.domain.Currency;
import com.loopdfs.rdas.domain.Language;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, thread-safe store for all reference data assembled during bootstrap.
 * Acts as the primary read model; the cache wrapping at the service layer
 * ensures this map survives across short-lived SOAP outages.
 */
@Component
public class ReferenceDataStore {

    /** Cache names – used in @Cacheable annotations and CacheConfig. */
    public static final String CACHE_COUNTRIES   = "countries";
    public static final String CACHE_CURRENCIES  = "currencies";
    public static final String CACHE_LANGUAGES   = "languages";
    public static final String CACHE_CONTINENTS  = "continents";

    private final Map<String, Country>   countries   = new ConcurrentHashMap<>();
    private final Map<String, Currency>  currencies  = new ConcurrentHashMap<>();
    private final Map<String, Language>  languages   = new ConcurrentHashMap<>();
    private final Map<String, Continent> continents  = new ConcurrentHashMap<>();

    // ── write ────────────────────────────────────────────────────────────────

    public void replaceCountries(Collection<Country> newCountries) {
        countries.clear();
        newCountries.forEach(c -> countries.put(c.getIsoCode().toUpperCase(), c));
    }

    public void replaceCurrencies(Collection<Currency> newCurrencies) {
        currencies.clear();
        newCurrencies.forEach(c -> currencies.put(c.getIsoCode().toUpperCase(), c));
    }

    public void replaceLanguages(Collection<Language> newLanguages) {
        languages.clear();
        newLanguages.forEach(l -> languages.put(l.getIsoCode().toUpperCase(), l));
    }

    public void replaceContinents(Collection<Continent> newContinents) {
        continents.clear();
        newContinents.forEach(c -> continents.put(c.getCode().toUpperCase(), c));
    }

    // ── read ─────────────────────────────────────────────────────────────────

    public List<Country> allCountries() {
        return List.copyOf(countries.values());
    }

    public Country findCountry(String isoCode) {
        return countries.get(isoCode.toUpperCase());
    }

    public List<Currency> allCurrencies() {
        return List.copyOf(currencies.values());
    }

    public List<Language> allLanguages() {
        return List.copyOf(languages.values());
    }

    public List<Continent> allContinents() {
        return List.copyOf(continents.values());
    }

    public boolean isEmpty() {
        return countries.isEmpty();
    }
}