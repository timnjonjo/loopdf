// controller/CurrencyController.java
package com.loopdfs.rdas.controllers;

import com.loopdfs.rdas.domain.Currency;
import com.loopdfs.rdas.services.ReferenceDataStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
@Tag(name = "Currencies", description = "Available ISO currencies")
public class CurrencyController {

    private final ReferenceDataStore store;

    @GetMapping
    @Operation(summary = "List all currencies")
    public ResponseEntity<List<Currency>> listCurrencies() {
        return ResponseEntity.ok(store.allCurrencies()
                .stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList());
    }
}