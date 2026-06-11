// controller/ContinentController.java
package com.loopdfs.rdas.controllers;

import com.loopdfs.rdas.domain.Continent;
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
@RequestMapping("/api/v1/continents")
@RequiredArgsConstructor
@Tag(name = "Continents", description = "Available continents")
public class ContinentController {

    private final ReferenceDataStore store;

    @GetMapping
    @Operation(summary = "List all continents")
    public ResponseEntity<List<Continent>> listContinents() {
        return ResponseEntity.ok(store.allContinents()
                .stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList());
    }
}