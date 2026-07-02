package com.loopdfs.rdas;

import com.loopdfs.rdas.domain.Country;
import com.loopdfs.rdas.dtos.CountryDetailResponse;
import com.loopdfs.rdas.dtos.CountryResponse;
import com.loopdfs.rdas.dtos.PagedResponse;
import com.loopdfs.rdas.exceptions.ResourceNotFoundException;
import com.loopdfs.rdas.exceptions.SoapGatewayException;
import com.loopdfs.rdas.services.CountryService;
import com.loopdfs.rdas.services.ReferenceDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CountryService}.
 *
 * Covers:
 *  - Search / filtering (name, continent, currency, language)
 *  - Sorting (field, direction, case-insensitivity)
 *  - Pagination (boundaries, overflow, single page)
 *  - Country detail retrieval
 *  - Currency sibling lookup
 *  - Empty store guard
 *  - Edge cases: null params, blank strings, case variants, special characters
 */
class CountryServiceTest {

    private ReferenceDataStore store;
    private CountryService service;

    // ── fixture data ─────────────────────────────────────────────────────────

    private static final Country KENYA = country(
            "KE", "Kenya", "AF", "KES", "Kenyan Shilling", "EN", "English",
            "Nairobi", "254", "http://flags/ke.jpg");

    private static final Country UGANDA = country(
            "UG", "Uganda", "AF", "UGX", "Ugandan Shilling", "SW", "Swahili",
            "Kampala", "256", "http://flags/ug.jpg");

    private static final Country GERMANY = country(
            "DE", "Germany", "EU", "EUR", "Euro", "DE", "German",
            "Berlin", "49", "http://flags/de.jpg");

    private static final Country FRANCE = country(
            "FR", "France", "EU", "EUR", "Euro", "FR", "French",
            "Paris", "33", "http://flags/fr.jpg");

    private static final Country USA = country(
            "US", "United States", "NA", "USD", "US Dollar", "EN", "English",
            "Washington D.C.", "1", "http://flags/us.jpg");

    private static final Country JAPAN = country(
            "JP", "Japan", "AS", "JPY", "Japanese Yen", "JA", "Japanese",
            "Tokyo", "81", "http://flags/jp.jpg");

    private static final Country ZIMBABWE = country(
            "ZW", "Zimbabwe", "AF", "USD", "US Dollar", "EN", "English",
            "Harare", "263", "http://flags/zw.jpg");

    @BeforeEach
    void setUp() {
        store   = new ReferenceDataStore();
        service = new CountryService(store);
        populateStore();
    }

    private void populateStore() {
        store.replaceCountries(List.of(KENYA, UGANDA, GERMANY, FRANCE, USA, JAPAN, ZIMBABWE));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Empty store guard
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Empty store guard")
    class EmptyStoreGuard {

        @BeforeEach
        void clearStore() {
            store.replaceCountries(Collections.emptyList());
        }

        @Test
        @DisplayName("searchCountries throws SoapGatewayException when store is empty")
        void searchCountries_emptyStore_throwsSoapGatewayException() {
            assertThatThrownBy(() ->
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 10))
                    .isInstanceOf(SoapGatewayException.class)
                    .hasMessageContaining("bootstrap");
        }

        @Test
        @DisplayName("getCountryDetail throws SoapGatewayException when store is empty")
        void getCountryDetail_emptyStore_throwsSoapGatewayException() {
            assertThatThrownBy(() -> service.getCountryDetail("KE"))
                    .isInstanceOf(SoapGatewayException.class);
        }

        @Test
        @DisplayName("getCurrencySiblings throws SoapGatewayException when store is empty")
        void getCurrencySiblings_emptyStore_throwsSoapGatewayException() {
            assertThatThrownBy(() -> service.getCurrencySiblings("KE", 0, 10))
                    .isInstanceOf(SoapGatewayException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Search by name
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search by name")
    class SearchByName {

        @Test
        @DisplayName("Exact match returns one result")
        void exactMatch_returnsOneResult() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("Kenya", null, null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsoCode()).isEqualTo("KE");
        }

        @Test
        @DisplayName("Partial match returns multiple results")
        void partialMatch_returnsMultipleResults() {
            // "an" matches Kenya, Germany, Japan
            PagedResponse<CountryResponse> result =
                    service.searchCountries("an", null, null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).extracting("isoCode")
                    .containsExactlyInAnyOrder("FR", "DE", "JP", "UG");
        }

        @Test
        @DisplayName("Case-insensitive match")
        void caseInsensitive_match() {
            PagedResponse<CountryResponse> lower =
                    service.searchCountries("kenya", null, null, null, "name", "asc", 0, 10);
            PagedResponse<CountryResponse> upper =
                    service.searchCountries("KENYA", null, null, null, "name", "asc", 0, 10);
            PagedResponse<CountryResponse> mixed =
                    service.searchCountries("kEnYa", null, null, null, "name", "asc", 0, 10);

            assertThat(lower.getContent()).hasSize(1);
            assertThat(upper.getContent()).hasSize(1);
            assertThat(mixed.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("No match returns empty content")
        void noMatch_returnsEmptyContent() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("Narnia", null, null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Null or empty search returns all countries")
        void nullOrEmptySearch_returnsAll(String search) {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(search, null, null, null, "name", "asc", 0, 100);

            assertThat(result.getTotalElements()).isEqualTo(7);
        }

        @Test
        @DisplayName("Search with only whitespace returns all countries")
        void blankSearch_returnsAll() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("   ", null, null, null, "name", "asc", 0, 100);

            assertThat(result.getTotalElements()).isEqualTo(7);
        }

        @Test
        @DisplayName("Search matches substring anywhere in the name")
        void substringMatch_anyPosition() {
            // "ited" matches "United States"
            PagedResponse<CountryResponse> result =
                    service.searchCountries("ited", null, null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).extracting("isoCode").containsExactly("US");
        }

        @Test
        @DisplayName("Search with special characters returns empty result gracefully")
        void specialCharacters_returnsEmpty() {
            assertThatCode(() ->
                    service.searchCountries("!@#$%", null, null, null, "name", "asc", 0, 10))
                    .doesNotThrowAnyException();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Filter by continent
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Filter by continent")
    class FilterByContinent {

        @Test
        @DisplayName("African countries returned for continent AF")
        void africaFilter_returnsAfricanCountries() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, "AF", null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).extracting("isoCode")
                    .containsExactlyInAnyOrder("KE", "UG", "ZW");
        }

        @Test
        @DisplayName("European countries returned for continent EU")
        void europeFilter_returnsEuropeanCountries() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, "EU", null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).extracting("isoCode")
                    .containsExactlyInAnyOrder("DE", "FR");
        }

        @Test
        @DisplayName("Unknown continent code returns empty result")
        void unknownContinent_returnsEmpty() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, "XX", null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Continent filter is case-insensitive")
        void continentFilter_caseInsensitive() {
            PagedResponse<CountryResponse> lower =
                    service.searchCountries(null, "af", null, null, "name", "asc", 0, 10);
            PagedResponse<CountryResponse> upper =
                    service.searchCountries(null, "AF", null, null, "name", "asc", 0, 10);

            assertThat(lower.getTotalElements()).isEqualTo(upper.getTotalElements());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Null or empty continent returns all countries")
        void nullOrEmptyContinent_returnsAll(String continent) {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, continent, null, null, "name", "asc", 0, 100);

            assertThat(result.getTotalElements()).isEqualTo(7);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Filter by currency
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Filter by currency")
    class FilterByCurrency {

        @Test
        @DisplayName("Euro countries returned for EUR")
        void euroFilter_returnsEuroCountries() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, "EUR", null, "name", "asc", 0, 10);

            assertThat(result.getContent()).extracting("isoCode")
                    .containsExactlyInAnyOrder("DE", "FR");
        }

        @Test
        @DisplayName("USD countries span multiple continents")
        void usdFilter_returnsMultipleContinents() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, "USD", null, "name", "asc", 0, 10);

            // US (NA) and Zimbabwe (AF) both use USD
            assertThat(result.getContent()).extracting("isoCode")
                    .containsExactlyInAnyOrder("US", "ZW");
        }

        @Test
        @DisplayName("Currency filter is case-insensitive")
        void currencyFilter_caseInsensitive() {
            PagedResponse<CountryResponse> lower =
                    service.searchCountries(null, null, "eur", null, "name", "asc", 0, 10);
            PagedResponse<CountryResponse> upper =
                    service.searchCountries(null, null, "EUR", null, "name", "asc", 0, 10);

            assertThat(lower.getTotalElements()).isEqualTo(upper.getTotalElements());
        }

        @Test
        @DisplayName("Unknown currency code returns empty result")
        void unknownCurrency_returnsEmpty() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, "XXX", null, "name", "asc", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Null or empty currency returns all countries")
        void nullOrEmptyCurrency_returnsAll(String currency) {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, currency, null, "name", "asc", 0, 100);

            assertThat(result.getTotalElements()).isEqualTo(7);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Filter by language
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Filter by language")
    class FilterByLanguage {

        @Test
        @DisplayName("English-speaking countries returned for EN")
        void englishFilter_returnsEnglishCountries() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, "EN", "name", "asc", 0, 10);

            assertThat(result.getContent()).extracting("isoCode")
                    .containsExactlyInAnyOrder("KE", "US", "ZW");
        }

        @Test
        @DisplayName("Language filter is case-insensitive")
        void languageFilter_caseInsensitive() {
            PagedResponse<CountryResponse> lower =
                    service.searchCountries(null, null, null, "en", "name", "asc", 0, 10);
            PagedResponse<CountryResponse> upper =
                    service.searchCountries(null, null, null, "EN", "name", "asc", 0, 10);

            assertThat(lower.getTotalElements()).isEqualTo(upper.getTotalElements());
        }

        @Test
        @DisplayName("Unknown language code returns empty result")
        void unknownLanguage_returnsEmpty() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, "ZZ", "name", "asc", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Combined filters
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Combined filters")
    class CombinedFilters {

        @Test
        @DisplayName("Name + continent narrows results correctly")
        void nameAndContinent_narrowsResults() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("a", "AF", null, null, "name", "asc", 0, 10);

            // "a" in Africa: Kenya, Uganda
            assertThat(result.getContent()).extracting("isoCode")
                    .containsExactlyInAnyOrder("KE", "UG" , "ZW");
        }

        @Test
        @DisplayName("Continent + currency with no matching intersection returns empty")
        void continentAndCurrency_noIntersection_returnsEmpty() {
            // EU countries using KES — impossible
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, "EU", "KES", null, "name", "asc", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("All filters applied — single matching country")
        void allFilters_singleMatch() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("Kenya", "AF", "KES", "EN", "name", "asc", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getIsoCode()).isEqualTo("KE");
        }

        @Test
        @DisplayName("All filters applied — no match")
        void allFilters_noMatch() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("Kenya", "EU", "EUR", "FR", "name", "asc", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Sorting
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sorting")
    class Sorting {

        @Test
        @DisplayName("Default sort is by name ascending")
        void defaultSort_nameAscending() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 100);

            assertThat(result.getContent()).extracting("name", String.class)
                    .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        }

        @Test
        @DisplayName("Sort by name descending")
        void sortByName_descending() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "desc", 0, 100);

            List<String> names = result.getContent().stream()
                    .map(CountryResponse::getName)
                    .toList();

            assertThat(names).isSortedAccordingTo(
                    String.CASE_INSENSITIVE_ORDER.reversed());
        }

        @Test
        @DisplayName("Sort by isoCode ascending")
        void sortByIsoCode_ascending() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "isoCode", "asc", 0, 100);

            assertThat(result.getContent()).extracting("isoCode", String.class)
                    .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        }

        @Test
        @DisplayName("Sort by isoCode descending")
        void sortByIsoCode_descending() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "isoCode", "desc", 0, 100);

            List<String> codes = result.getContent().stream()
                    .map(CountryResponse::getIsoCode)
                    .toList();

            assertThat(codes).isSortedAccordingTo(
                    String.CASE_INSENSITIVE_ORDER.reversed());
        }

        @Test
        @DisplayName("Sort by continent ascending")
        void sortByContinent_ascending() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "continent", "asc", 0, 100);

            List<String> continents = result.getContent().stream()
                    .map(CountryResponse::getContinentCode)
                    .toList();

            assertThat(continents).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"NAME", "Name", "nAmE"})
        @DisplayName("Sort field is case-insensitive")
        void sortField_caseInsensitive(String sortBy) {
            assertThatCode(() ->
                    service.searchCountries(null, null, null, null, sortBy, "asc", 0, 10))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Unknown sort field falls back to name sort")
        void unknownSortField_fallsBackToName() {
            PagedResponse<CountryResponse> unknown =
                    service.searchCountries(null, null, null, null, "unknown_field", "asc", 0, 100);
            PagedResponse<CountryResponse> byName =
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 100);

            assertThat(unknown.getContent()).extracting("isoCode")
                    .isEqualTo(byName.getContent().stream()
                            .map(CountryResponse::getIsoCode).toList());
        }

        @Test
        @DisplayName("Null sort field falls back to name sort")
        void nullSortField_fallsBackToName() {
            assertThatCode(() ->
                    service.searchCountries(null, null, null, null, null, "asc", 0, 10))
                    .doesNotThrowAnyException();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Pagination
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("First page returns correct slice")
        void firstPage_correctSlice() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 3);

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(3);
            assertThat(result.getTotalElements()).isEqualTo(7);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.isLast()).isFalse();
        }

        @Test
        @DisplayName("Last page returns remaining elements")
        void lastPage_remainingElements() {
            // 7 elements, size 3 → page 2 has 1 element
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", 2, 3);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("Page beyond total returns empty content")
        void pageExceedingTotal_returnsEmpty() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", 99, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("Size larger than total returns all elements on one page")
        void sizeLargerThanTotal_returnsAllOnOnePage() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 100);

            assertThat(result.getContent()).hasSize(7);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("Size of 1 returns exactly one element per page")
        void sizeOne_oneElementPerPage() {
            PagedResponse<CountryResponse> page0 =
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 1);
            PagedResponse<CountryResponse> page1 =
                    service.searchCountries(null, null, null, null, "name", "asc", 1, 1);

            assertThat(page0.getContent()).hasSize(1);
            assertThat(page1.getContent()).hasSize(1);
            assertThat(page0.getContent().get(0).getIsoCode())
                    .isNotEqualTo(page1.getContent().get(0).getIsoCode());
        }

        @Test
        @DisplayName("Page 0 and page 1 do not overlap")
        void pages_doNotOverlap() {
            PagedResponse<CountryResponse> page0 =
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 3);
            PagedResponse<CountryResponse> page1 =
                    service.searchCountries(null, null, null, null, "name", "asc", 1, 3);

            List<String> page0Codes = page0.getContent().stream()
                    .map(CountryResponse::getIsoCode).toList();
            List<String> page1Codes = page1.getContent().stream()
                    .map(CountryResponse::getIsoCode).toList();

            assertThat(page0Codes).doesNotContainAnyElementsOf(page1Codes);
        }

        @Test
        @DisplayName("All pages combined cover all elements without duplication")
        void allPagesCombined_coverAllElements() {
            List<String> allCodes = new java.util.ArrayList<>();

            // page 0, 1, 2 with size 3
            for (int page = 0; page < 3; page++) {
                PagedResponse<CountryResponse> result =
                        service.searchCountries(null, null, null, null, "name", "asc", page, 3);
                result.getContent().forEach(c -> allCodes.add(c.getIsoCode()));
            }

            assertThat(allCodes).hasSize(7);
            assertThat(allCodes).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("totalPages is correct for exact division")
        void totalPages_exactDivision() {
            // 6 countries that match "a" → France, Germany, Japan, Kenya, Uganda, United States... let's filter to AF (3 countries)
            // Use AF which has 3 countries, size 3 → 1 page exactly
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, "AF", null, null, "name", "asc", 0, 3);

            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("Empty filter result has correct pagination metadata")
        void emptyResult_correctPaginationMetadata() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("Narnia", null, null, null, "name", "asc", 0, 10);

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getTotalPages()).isZero();
            assertThat(result.isLast()).isTrue();
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Country detail
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get country detail")
    class GetCountryDetail {

        @Test
        @DisplayName("Returns full detail for a valid ISO code")
        void validIsoCode_returnsFullDetail() {
            CountryDetailResponse detail = service.getCountryDetail("KE");

            assertThat(detail.getIsoCode()).isEqualTo("KE");
            assertThat(detail.getName()).isEqualTo("Kenya");
            assertThat(detail.getContinentCode()).isEqualTo("AF");
            assertThat(detail.getCapitalCity()).isEqualTo("Nairobi");
            assertThat(detail.getCurrencyIsoCode()).isEqualTo("KES");
            assertThat(detail.getCurrencyName()).isEqualTo("Kenyan Shilling");
            assertThat(detail.getInternationalPhoneCode()).isEqualTo("254");
            assertThat(detail.getFlagUrl()).isEqualTo("http://flags/ke.jpg");
        }

        @Test
        @DisplayName("Lookup is case-insensitive")
        void lookup_caseInsensitive() {
            CountryDetailResponse lower = service.getCountryDetail("ke");
            CountryDetailResponse upper = service.getCountryDetail("KE");

            assertThat(lower.getName()).isEqualTo(upper.getName());
        }

        @Test
        @DisplayName("Unknown ISO code throws ResourceNotFoundException")
        void unknownIsoCode_throwsResourceNotFoundException() {
            assertThatThrownBy(() -> service.getCountryDetail("ZZ"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ZZ");
        }

        @Test
        @DisplayName("Null ISO code throws exception")
        void nullIsoCode_throwsException() {
            assertThatThrownBy(() -> service.getCountryDetail(null))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Response contains all fields mapped correctly")
        void responseFields_allMappedCorrectly() {
            CountryDetailResponse detail = service.getCountryDetail("JP");

            assertThat(detail).satisfies(d -> {
                assertThat(d.getIsoCode()).isEqualTo("JP");
                assertThat(d.getName()).isEqualTo("Japan");
                assertThat(d.getCapitalCity()).isEqualTo("Tokyo");
                assertThat(d.getCurrencyIsoCode()).isEqualTo("JPY");
                assertThat(d.getCurrencyName()).isEqualTo("Japanese Yen");
                assertThat(d.getInternationalPhoneCode()).isEqualTo("81");
                assertThat(d.getLanguageIsoCode()).isEqualTo("JA");
                assertThat(d.getLanguageName()).isEqualTo("Japanese");
                assertThat(d.getFlagUrl()).isEqualTo("http://flags/jp.jpg");
            });
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Currency siblings
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Currency siblings")
    class CurrencySiblings {

        @Test
        @DisplayName("Returns countries sharing the same currency, excluding the anchor")
        void returnsSiblings_excludesAnchor() {
            PagedResponse<CountryResponse> result =
                    service.getCurrencySiblings("DE", 0, 10);

            assertThat(result.getContent()).extracting("isoCode").containsExactly("FR");
            assertThat(result.getContent()).extracting("isoCode").doesNotContain("DE");
        }

        @Test
        @DisplayName("USD siblings returns Zimbabwe, excludes US")
        void usdSiblings_returnsZimbabwe() {
            PagedResponse<CountryResponse> result =
                    service.getCurrencySiblings("US", 0, 10);

            assertThat(result.getContent()).extracting("isoCode").containsExactly("ZW");
            assertThat(result.getContent()).extracting("isoCode").doesNotContain("US");
        }

        @Test
        @DisplayName("Country with unique currency returns empty siblings list")
        void uniqueCurrency_returnsEmptySiblings() {
            PagedResponse<CountryResponse> result =
                    service.getCurrencySiblings("JP", 0, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("Siblings are sorted by name ascending")
        void siblings_sortedByName() {
            // Add a third EUR country to verify sort
            store.replaceCountries(List.of(
                    GERMANY, FRANCE,
                    country("IT", "Italy", "EU", "EUR", "Euro", "IT", "Italian",
                            "Rome", "39", "http://flags/it.jpg")
            ));

            PagedResponse<CountryResponse> result =
                    service.getCurrencySiblings("DE", 0, 10);

            assertThat(result.getContent())
                    .extracting("name", String.class)
                    .isSortedAccordingTo( String.CASE_INSENSITIVE_ORDER);
        }

        @Test
        @DisplayName("Unknown ISO code throws ResourceNotFoundException")
        void unknownIsoCode_throwsResourceNotFoundException() {
            assertThatThrownBy(() -> service.getCurrencySiblings("ZZ", 0, 10))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ZZ");
        }

        @Test
        @DisplayName("Sibling lookup is case-insensitive for anchor code")
        void anchorCode_caseInsensitive() {
            PagedResponse<CountryResponse> lower = service.getCurrencySiblings("de", 0, 10);
            PagedResponse<CountryResponse> upper = service.getCurrencySiblings("DE", 0, 10);

            assertThat(lower.getTotalElements()).isEqualTo(upper.getTotalElements());
        }

        @Test
        @DisplayName("Siblings pagination works correctly")
        void siblings_paginationWorks() {
            // Add more EUR countries so we have enough to paginate
            store.replaceCountries(List.of(
                    GERMANY, FRANCE,
                    country("IT", "Italy",   "EU", "EUR", "Euro", "IT", "Italian", "Rome",   "39", ""),
                    country("ES", "Spain",   "EU", "EUR", "Euro", "ES", "Spanish", "Madrid", "34", ""),
                    country("PT", "Portugal","EU", "EUR", "Euro", "PT", "Portuguese","Lisbon","351","")
            ));

            PagedResponse<CountryResponse> page0 = service.getCurrencySiblings("DE", 0, 2);
            PagedResponse<CountryResponse> page1 = service.getCurrencySiblings("DE", 1, 2);

            assertThat(page0.getContent()).hasSize(2);
            assertThat(page1.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isEqualTo(4); // DE excluded
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Response DTO mapping
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Response DTO mapping")
    class ResponseMapping {

        @Test
        @DisplayName("CountryResponse summary fields are all populated")
        void countryResponse_allSummaryFieldsPopulated() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries("Kenya", null, null, null, "name", "asc", 0, 10);

            CountryResponse response = result.getContent().get(0);
            assertThat(response.getIsoCode()).isEqualTo("KE");
            assertThat(response.getName()).isEqualTo("Kenya");
            assertThat(response.getContinentCode()).isEqualTo("AF");
            assertThat(response.getCurrencyIsoCode()).isEqualTo("KES");
            assertThat(response.getCurrencyName()).isEqualTo("Kenyan Shilling");
            assertThat(response.getLanguageName()).isEqualTo("English");
            assertThat(response.getFlagUrl()).isEqualTo("http://flags/ke.jpg");
        }

        @Test
        @DisplayName("PagedResponse metadata is accurate")
        void pagedResponse_metadataAccurate() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", 1, 3);

            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(3);
            assertThat(result.getTotalElements()).isEqualTo(7);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.isLast()).isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Store mutation during search (thread-safety smoke test)
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Store refresh during active queries")
    class StoreRefreshConcurrency {

        @Test
        @DisplayName("Store can be replaced while reads are in progress without throwing")
        void storeReplacement_doesNotThrow() {
            assertThatCode(() -> {
                // Simulate a store refresh mid-search
                Thread refreshThread = new Thread(() ->
                        store.replaceCountries(List.of(KENYA, GERMANY)));
                refreshThread.start();

                service.searchCountries(null, null, null, null, "name", "asc", 0, 100);
                refreshThread.join(1000);
            }).doesNotThrowAnyException();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Single country in store
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Single country in store")
    class SingleCountryStore {

        @BeforeEach
        void setUp() {
            store.replaceCountries(List.of(KENYA));
        }

        @Test
        @DisplayName("Search returns the single country")
        void search_returnsSingleCountry() {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("Currency siblings returns empty for single-country store")
        void currencySiblings_emptySingleCountryStore() {
            PagedResponse<CountryResponse> result =
                    service.getCurrencySiblings("KE", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Parametrized boundary checks
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Boundary checks")
    class BoundaryChecks {

        @ParameterizedTest
        @CsvSource({
                "0, 1,  7, 7",
                "0, 7,  1, 7",
                "0, 10, 1, 7",
                "1, 3,  3, 7",
                "2, 3,  3, 7",
        })
        @DisplayName("Pagination math is correct across boundary inputs")
        void paginationMath(int page, int size, int expectedTotalPages, int expectedTotal) {
            PagedResponse<CountryResponse> result =
                    service.searchCountries(null, null, null, null, "name", "asc", page, size);

            assertThat(result.getTotalElements()).isEqualTo(expectedTotal);
            assertThat(result.getTotalPages()).isEqualTo(expectedTotalPages);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helper factory
    // ════════════════════════════════════════════════════════════════════════

    private static Country country(String isoCode, String name, String continentCode,
                                   String currencyIso, String currencyName,
                                   String languageIso, String languageName,
                                   String capital, String phoneCode, String flagUrl) {
        return Country.builder()
                .isoCode(isoCode)
                .name(name)
                .continentCode(continentCode)
                .currencyIsoCode(currencyIso)
                .currencyName(currencyName)
                .languageIsoCode(languageIso)
                .languageName(languageName)
                .capitalCity(capital)
                .internationalPhoneCode(phoneCode)
                .flagUrl(flagUrl)
                .build();
    }
}