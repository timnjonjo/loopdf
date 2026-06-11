// service/CountryService.java
package com.loopdfs.rdas.services;

import com.loopdfs.rdas.domain.Country;
import com.loopdfs.rdas.dtos.CountryDetailResponse;
import com.loopdfs.rdas.dtos.CountryResponse;
import com.loopdfs.rdas.dtos.PagedResponse;
import com.loopdfs.rdas.exceptions.ResourceNotFoundException;
import com.loopdfs.rdas.exceptions.SoapGatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * Core service: filters, sorts, paginates, and maps Country domain objects.
 * All reads come from the in-memory {@link ReferenceDataStore}; no SOAP calls here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountryService {

    private final ReferenceDataStore store;

    /**
     * Search countries with optional filters, sorting, and pagination.
     *
     * @param search        substring match on country name (case-insensitive)
     * @param continentCode exact ISO continent code
     * @param currencyCode  exact ISO currency code
     * @param languageCode  exact ISO language code
     * @param sortBy        field to sort by: "name" (default) or "isoCode"
     * @param sortDir       "asc" (default) or "desc"
     * @param page          zero-based page index
     * @param size          page size (1–100)
     */
    public PagedResponse<CountryResponse> searchCountries(
            String search,
            String continentCode,
            String currencyCode,
            String languageCode,
            String sortBy,
            String sortDir,
            int page,
            int size) {

        ensureStorePopulated();

        List<Country> filtered = store.allCountries().stream()
                .peek(System.out::println)
                .filter(c -> matchesSearch(c, search))
                .filter(c -> matchesContinent(c, continentCode))
                .filter(c -> matchesCurrency(c, currencyCode))
                .filter(c -> matchesLanguage(c, languageCode))
                .sorted(buildComparator(sortBy, sortDir))
                .toList();

        return paginate(filtered, page, size);
    }

    /**
     * Retrieve full detail for a single country by ISO code.
     */
    public CountryDetailResponse getCountryDetail(String isoCode) {
        ensureStorePopulated();

        Country country = store.findCountry(isoCode);
        if (country == null) {
            throw new ResourceNotFoundException("Country not found: " + isoCode);
        }
        return toDetailResponse(country);
    }

    /**
     * Returns all countries sharing the same currency as the given country.
     */
    public PagedResponse<CountryResponse> getCurrencySiblings(
            String isoCode, int page, int size) {

        ensureStorePopulated();

        Country anchor = store.findCountry(isoCode);
        if (anchor == null) {
            throw new ResourceNotFoundException("Country not found: " + isoCode);
        }

        String currencyIso = anchor.getCurrencyIsoCode();
        List<Country> siblings = store.allCountries().stream()
                .filter(c -> currencyIso.equalsIgnoreCase(c.getCurrencyIsoCode()))
                .filter(c -> !c.getIsoCode().equalsIgnoreCase(isoCode))
                .sorted(Comparator.comparing(Country::getName))
                .toList();

        return paginate(siblings, page, size);
    }

    // ── filtering predicates ─────────────────────────────────────────────────

    private boolean matchesSearch(Country c, String search) {
        return !StringUtils.hasText(search)
                || c.getName().toLowerCase().contains(search.toLowerCase());
    }

    private boolean matchesContinent(Country c, String continentCode) {
        return !StringUtils.hasText(continentCode)
                || continentCode.equalsIgnoreCase(c.getContinentCode());
    }

    private boolean matchesCurrency(Country c, String currencyCode) {
        return !StringUtils.hasText(currencyCode)
                || currencyCode.equalsIgnoreCase(c.getCurrencyIsoCode());
    }

    private boolean matchesLanguage(Country c, String languageCode) {
        return !StringUtils.hasText(languageCode)
                || languageCode.equalsIgnoreCase(c.getLanguageIsoCode());
    }

    // ── sorting ──────────────────────────────────────────────────────────────

    private Comparator<Country> buildComparator(String sortBy, String sortDir) {
        Comparator<Country> comparator = switch (sortBy == null ? "name" : sortBy.toLowerCase()) {
            case "isocode" -> Comparator.comparing(Country::getIsoCode);
            case "continent" -> Comparator.comparing(Country::getContinentCode);
            default         -> Comparator.comparing(Country::getName);
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    // ── pagination ───────────────────────────────────────────────────────────

    private PagedResponse<CountryResponse> paginate(List<Country> items, int page, int size) {
        int totalElements = items.size();
        int totalPages    = (int) Math.ceil((double) totalElements / size);
        int fromIndex     = Math.min(page * size, totalElements);
        int toIndex       = Math.min(fromIndex + size, totalElements);

        List<CountryResponse> pageContent = items.subList(fromIndex, toIndex)
                .stream()
                .map(this::toSummaryResponse)
                .toList();

        return PagedResponse.<CountryResponse>builder()
                .content(pageContent)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }

    // ── mappers ──────────────────────────────────────────────────────────────

    private CountryResponse toSummaryResponse(Country c) {
        return CountryResponse.builder()
                .isoCode(c.getIsoCode())
                .name(c.getName())
                .continentCode(c.getContinentCode())
                .currencyIsoCode(c.getCurrencyIsoCode())
                .currencyName(c.getCurrencyName())
                .languageName(c.getLanguageName())
                .flagUrl(c.getFlagUrl())
                .build();
    }

    private CountryDetailResponse toDetailResponse(Country c) {
        return CountryDetailResponse.builder()
                .isoCode(c.getIsoCode())
                .name(c.getName())
                .continentCode(c.getContinentCode())
                .capitalCity(c.getCapitalCity())
                .currencyIsoCode(c.getCurrencyIsoCode())
                .currencyName(c.getCurrencyName())
                .internationalPhoneCode(c.getInternationalPhoneCode())
                .languageIsoCode(c.getLanguageIsoCode())
                .languageName(c.getLanguageName())
                .flagUrl(c.getFlagUrl())
                .build();
    }

    // ── guard ────────────────────────────────────────────────────────────────

    private void ensureStorePopulated() {
        if (store.isEmpty()) {
            throw new SoapGatewayException(
                    "Reference data not yet available – bootstrap may have failed.", null);
        }
    }
}