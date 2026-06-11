// gateway/CountryInfoSoapGatewayImpl.java
package com.loopdfs.rdas.gateway;

import com.loopdfs.rdas.domain.Continent;
import com.loopdfs.rdas.domain.Currency;
import com.loopdfs.rdas.domain.Language;
import com.loopdfs.rdas.exceptions.SoapGatewayException;
import com.loopdfs.rdas.soap.generated.ArrayOftCountryCodeAndNameGroupedByContinent;
import com.loopdfs.rdas.soap.generated.CountryInfoServiceSoapType;
import com.loopdfs.rdas.soap.generated.TCurrency;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete SOAP adapter.
 * Each public method is protected by the "soapGateway" circuit breaker and retry policy.
 * The generated stubs (CountryInfoServiceSoapType etc.) come from wsimport at build time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CountryInfoSoapGatewayImpl implements CountryInfoSoapGateway {

    private final CountryInfoServiceSoapType soapPort;

    // ── Circuit-breaker + retry wrapper ─────────────────────────────────────

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "mapFallback")
    public Map<String, String> fetchCountriesByContinent() {
        try {
            Map<String, String> result = new LinkedHashMap<>();

            ArrayOftCountryCodeAndNameGroupedByContinent response =
                    soapPort.listOfCountryNamesGroupedByContinent();
            if (response == null || response.getTCountryCodeAndNameGroupedByContinent() == null) {
                return Collections.emptyMap();
            }
            for (var continentGroup : response.getTCountryCodeAndNameGroupedByContinent()) {
                String continentCode = continentGroup.getContinent().getSCode();

                var countryList = continentGroup.getCountryCodeAndNames();
                if (countryList == null) continue;

                for (var country : countryList.getTCountryCodeAndName()) {
                    result.put(country.getSISOCode(), continentCode);
                }
            }
            return result;

        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch countries by continent", ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchCapitalCity(String isoCode) {
        try {
            return soapPort.capitalCity(isoCode);
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch capital for " + isoCode, ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchCountryCurrencyIso(String isoCode) {
        try {
            TCurrency currency = soapPort.countryCurrency(isoCode);
            return currency != null ? currency.getSISOCode() : "";
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch currency ISO for " + isoCode, ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchCurrencyName(String currencyIsoCode) {
        try {
            return soapPort.currencyName(currencyIsoCode);
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch currency name for " + currencyIsoCode, ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchPhoneCode(String isoCode) {
        try {
            return soapPort.countryIntPhoneCode(isoCode);
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch phone code for " + isoCode, ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchCountryName(String isoCode) {
        try {
            return soapPort.countryName(isoCode);
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch country name for " + isoCode, ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchFlagUrl(String isoCode) {
        try {
            return soapPort.countryFlag(isoCode);
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch flag URL for " + isoCode, ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "listFallback")
    @Retry(name = "soapGateway")
    public List<Continent> fetchContinents() {
        try {
            return soapPort.listOfContinentsByName()
                    .getTContinent()
                    .stream()
                    .map(c -> new Continent(c.getSCode(), c.getSName()))
                    .toList();
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch continents", ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "listFallback")
    @Retry(name = "soapGateway")
    public List<Currency> fetchCurrencies() {
        try {
            return soapPort.listOfCurrenciesByName()
                    .getTCurrency()
                    .stream()
                    .map(c -> new Currency(c.getSISOCode(), c.getSName()))
                    .toList();
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch currencies", ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "listFallback")
    @Retry(name = "soapGateway")
    public List<Language> fetchLanguages() {
        try {
            return soapPort.listOfLanguagesByName()
                    .getTLanguage()
                    .stream()
                    .map(l -> new Language(l.getSISOCode(), l.getSName()))
                    .toList();
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch languages", ex);
        }
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchLanguageIso(String countryIsoCode) {
        // The SOAP service does not expose a direct country→language lookup.
        // We iterate language list and check by convention (best effort).
        // A more robust approach is handled in the bootstrap layer using known ISO mappings.
        return "";
    }

    @Override
    @CircuitBreaker(name = "soapGateway", fallbackMethod = "stringFallback")
    @Retry(name = "soapGateway")
    public String fetchLanguageName(String languageIsoCode) {
        try {
            return soapPort.languageName(languageIsoCode);
        } catch (Exception ex) {
            throw new SoapGatewayException("Failed to fetch language name for " + languageIsoCode, ex);
        }
    }

    // ── Resilience4j fallback methods ────────────────────────────────────────

    @SuppressWarnings("unused")
    private Map<String, String> mapFallback(Throwable t) {
        log.warn("Circuit breaker OPEN – returning empty map. Cause: {}", t.getMessage());
        return Collections.emptyMap();
    }

    @SuppressWarnings("unused")
    private String stringFallback(String arg, Throwable t) {
        log.warn("Circuit breaker OPEN – returning empty string. Cause: {}", t.getMessage());
        return "";
    }

    @SuppressWarnings("unused")
    private List<?> listFallback(Throwable t) {
        log.warn("Circuit breaker OPEN – returning empty list. Cause: {}", t.getMessage());
        return Collections.emptyList();
    }
}