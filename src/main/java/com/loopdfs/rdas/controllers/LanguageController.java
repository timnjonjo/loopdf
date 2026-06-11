// controller/LanguageController.java
package com.loopdfs.rdas.controllers;

import com.loopdfs.rdas.domain.Language;
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
@RequestMapping("/api/v1/languages")
@RequiredArgsConstructor
@Tag(name = "Languages", description = "Available ISO languages")
public class LanguageController {

    private final ReferenceDataStore store;

    @GetMapping
    @Operation(summary = "List all languages")
    public ResponseEntity<List<Language>> listLanguages() {
        return ResponseEntity.ok(store.allLanguages()
                .stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList());
    }
}