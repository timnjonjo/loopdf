// config/CacheConfig.java
package com.loopdfs.rdas.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Named Caffeine caches with per-type TTLs.
 * Cache names are constants on {@link com.loopdfs.rdas.services.ReferenceDataStore}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${rdas.cache.country-ttl-hours:24}")
    private long countryTtlHours;

    @Value("${rdas.cache.currency-ttl-hours:24}")
    private long currencyTtlHours;

    @Value("${rdas.cache.language-ttl-hours:24}")
    private long languageTtlHours;

    @Value("${rdas.cache.continent-ttl-hours:72}")
    private long continentTtlHours;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("countries",
                Caffeine.newBuilder().expireAfterWrite(countryTtlHours, TimeUnit.HOURS)
                        .maximumSize(300).build());
        manager.registerCustomCache("currencies",
                Caffeine.newBuilder().expireAfterWrite(currencyTtlHours, TimeUnit.HOURS)
                        .maximumSize(300).build());
        manager.registerCustomCache("languages",
                Caffeine.newBuilder().expireAfterWrite(languageTtlHours, TimeUnit.HOURS)
                        .maximumSize(500).build());
        manager.registerCustomCache("continents",
                Caffeine.newBuilder().expireAfterWrite(continentTtlHours, TimeUnit.HOURS)
                        .maximumSize(20).build());
        return manager;
    }
}