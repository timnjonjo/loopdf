// service/ReferenceDataBootstrapService.java
package com.loopdfs.rdas.services;

import com.loopdfs.rdas.domain.Continent;
import com.loopdfs.rdas.domain.Country;
import com.loopdfs.rdas.domain.Currency;
import com.loopdfs.rdas.domain.Language;
import com.loopdfs.rdas.gateway.CountryInfoSoapGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Loads all reference data from SOAP on startup and refreshes nightly.
 * Single bootstrap run = ~6 SOAP calls total, regardless of request volume.
 *
 * Strategy:
 *  1. Fetch the continent→country mapping (1 SOAP call per continent via groupedByContinent).
 *  2. For each country ISO, fetch capital, currency, phone code, name, flag (5 operations).
 *     These are batched sequentially; no per-request SOAP calls after boot.
 *  3. Currencies and languages fetched as lists (2 calls).
 *
 * NOTE: The SOAP service has ~250 countries. At 100 req/min, a full sequential bootstrap
 * would take ~3 minutes. For production, consider parallel batching with a semaphore
 * limiting concurrency to stay within rate limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceDataBootstrapService {

    private final CountryInfoSoapGateway soapGateway;
    private final ReferenceDataStore     store;
    private final Semaphore soapRateLimiter = new Semaphore(8);

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Starting reference data bootstrap on application ready...");
        refresh();
    }

    /**
     * Scheduled nightly refresh at 02:00.
     * Stale data continues to be served from the store during the refresh window.
     */
    @Scheduled(cron = "${rdas.bootstrap.refresh-cron:0 0 2 * * *}")
    public void scheduledRefresh() {
        log.info("Running scheduled reference data refresh...");
        refresh();
    }

    // ── core bootstrap logic ─────────────────────────────────────────────────

    /**
     * Fetches and assembles all reference data.
     * Designed to be idempotent and safe to call multiple times.
     */
    public void refresh() {
        try {
            refreshContinents();
            refreshCurrencies();
            refreshLanguages();
            refreshCountries();
            log.info("Reference data bootstrap complete. countries={}, currencies={}, languages={}",
                    store.allCountries().size(),
                    store.allCurrencies().size(),
                    store.allLanguages().size());
        } catch (Exception ex) {
            log.error("Bootstrap failed – existing store data will be retained if available. Error: {}",
                    ex.getMessage(), ex);
        }
    }

    private void refreshContinents() {
        List<Continent> continents = soapGateway.fetchContinents();
        store.replaceContinents(continents);
        log.debug("Loaded {} continents", continents.size());
    }

    private void refreshCurrencies() {
        List<Currency> currencies = soapGateway.fetchCurrencies();
        store.replaceCurrencies(currencies);
        log.debug("Loaded {} currencies", currencies.size());
    }

    private void refreshLanguages() {
        List<Language> languages = soapGateway.fetchLanguages();
        store.replaceLanguages(languages);
        log.debug("Loaded {} languages", languages.size());
    }

    private void refreshCountries() {
        log.info("Fetching country-continent mapping from SOAP...");
        Map<String, String> isoToContinentCode = soapGateway.fetchCountriesByContinent();
        log.info("SOAP returned {} country entries", isoToContinentCode.size());

        if (isoToContinentCode.isEmpty()) {
            log.error("fetchCountriesByContinent returned empty map — skipping country refresh");
            return;
        }

        int threadCount =10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "bootstrap-worker");
            t.setDaemon(true);
            return t;
        });
        List<Country> assembled = new CopyOnWriteArrayList<>();
        // Build one task per country
        List<CompletableFuture<Void>> futures = isoToContinentCode.entrySet()
                .stream()
                .map(entry -> CompletableFuture.runAsync(
                        () -> fetchAndAssembleCountry(
                                entry.getKey(),
                                entry.getValue(),
                                assembled),
                        executor))
                .toList();

        // Wait for all tasks, with a generous timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            log.error("Country bootstrap timed out after 10 minutes. Assembled {} so far.", assembled.size());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Country bootstrap interrupted", ex);
        } catch (ExecutionException ex) {
            log.error("Country bootstrap execution error", ex.getCause());
        } finally {
            executor.shutdown();
        }

        log.info("Assembled {} / {} countries", assembled.size(), isoToContinentCode.size());

        if (!assembled.isEmpty()) {
            store.replaceCountries(assembled);
        }
    }

    private void fetchAndAssembleCountry(String isoCode, String continentCode,
                                         List<Country> assembled) {
        try {
            log.debug("Fetching details for country: {}", isoCode);
            String name         = callWithRateLimit(() -> soapGateway.fetchCountryName(isoCode));
            String capitalCity  = callWithRateLimit(() -> soapGateway.fetchCapitalCity(isoCode));
            String currencyIso  = callWithRateLimit(() -> soapGateway.fetchCountryCurrencyIso(isoCode));
            String currencyName = currencyIso.isBlank()
                    ? "" : callWithRateLimit(() -> soapGateway.fetchCurrencyName(currencyIso));
            String phoneCode    = callWithRateLimit(() -> soapGateway.fetchPhoneCode(isoCode));
            String flagUrl      = callWithRateLimit(() -> soapGateway.fetchFlagUrl(isoCode));

            assembled.add(Country.builder()
                    .isoCode(isoCode)
                    .name(name)
                    .continentCode(continentCode)
                    .capitalCity(capitalCity)
                    .currencyIsoCode(currencyIso)
                    .currencyName(currencyName)
                    .internationalPhoneCode(phoneCode)
                    .languageIsoCode("")
                    .languageName("")
                    .flagUrl(flagUrl)
                    .build());

        } catch (Exception ex) {
            log.warn("Skipping country {} due to error: {}", isoCode, ex.getMessage());
        }
    }

    private String callWithRateLimit(CheckedSupplier<String> soapCall) {
        try {
            soapRateLimiter.acquire();
            try {
                return soapCall.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                soapRateLimiter.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    // CheckedSupplier — simple functional interface since java's Supplier doesn't throw
    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}