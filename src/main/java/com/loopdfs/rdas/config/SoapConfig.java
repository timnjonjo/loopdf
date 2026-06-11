// config/SoapConfig.java
package com.loopdfs.rdas.config;

import com.loopdfs.rdas.soap.generated.CountryInfoService;
import com.loopdfs.rdas.soap.generated.CountryInfoServiceSoapType;
import jakarta.xml.ws.BindingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configures the JAX-WS SOAP port with timeout overrides from application.yml.
 * The port is thread-safe and reused across all requests.
 */
@Slf4j
@Configuration
public class SoapConfig {

    @Value("${rdas.soap.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${rdas.soap.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Bean
    public CountryInfoServiceSoapType countryInfoServiceSoapPort() {
        log.info("Initialising CountryInfo SOAP port (connectTimeout={}ms, readTimeout={}ms)",
                connectTimeoutMs, readTimeoutMs);

        CountryInfoServiceSoapType port = new CountryInfoService().getCountryInfoServiceSoap();

        Map<String, Object> requestContext = ((BindingProvider) port).getRequestContext();
        requestContext.put("com.sun.xml.ws.connect.timeout", connectTimeoutMs);
        requestContext.put("com.sun.xml.ws.request.timeout", readTimeoutMs);

        return port;
    }
}