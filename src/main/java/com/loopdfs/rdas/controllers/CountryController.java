// controller/CountryController.java
package com.loopdfs.rdas.controllers;

import com.loopdfs.rdas.dtos.CountryDetailResponse;
import com.loopdfs.rdas.dtos.CountryResponse;
import com.loopdfs.rdas.dtos.PagedResponse;
import com.loopdfs.rdas.services.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
@Tag(name = "Countries", description = "Country search, filter, and detail operations")
public class CountryController {

    private final CountryService countryService;

    @GetMapping
    @Operation(summary = "Search and filter countries with pagination and sorting")
    public ResponseEntity<PagedResponse<CountryResponse>> searchCountries(

            @Parameter(description = "Partial country name match (case-insensitive)")
            @RequestParam(required = false) String search,

            @Parameter(description = "ISO continent code (e.g. AF, EU, AS)")
            @RequestParam(required = false) String continent,

            @Parameter(description = "ISO 4217 currency code (e.g. KES, USD)")
            @RequestParam(required = false) String currency,

            @Parameter(description = "ISO 639-1 language code (e.g. EN, FR)")
            @RequestParam(required = false) String language,

            @Parameter(description = "Sort field: name | isoCode | continent")
            @RequestParam(defaultValue = "name") String sortBy,

            @Parameter(description = "Sort direction: asc | desc")
            @RequestParam(defaultValue = "asc")
            @Pattern(regexp = "^(asc|desc)$", message = "sortDir must be 'asc' or 'desc'")
            String sortDir,

            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be >= 0")
            int page,

            @Parameter(description = "Page size (1–100)")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size) {

        return ResponseEntity.ok(
                countryService.searchCountries(search, continent, currency, language,
                        sortBy, sortDir, page, size));
    }

    @GetMapping("/{isoCode}")
    @Operation(summary = "Retrieve full details for a country by ISO 3166-1 alpha-2 code")
    public ResponseEntity<CountryDetailResponse> getCountry(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "isoCode must be a 2-letter ISO code")
            String isoCode) {

        return ResponseEntity.ok(countryService.getCountryDetail(isoCode.toUpperCase()));
    }

    @GetMapping("/{isoCode}/currency-siblings")
    @Operation(summary = "Retrieve other countries sharing the same currency")
    public ResponseEntity<PagedResponse<CountryResponse>> getCurrencySiblings(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "isoCode must be a 2-letter ISO code")
            String isoCode,

            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(
                countryService.getCurrencySiblings(isoCode.toUpperCase(), page, size));
    }
}