package com.stockanalysis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * AppConfig — application-level Spring beans.
 *
 * RestTemplate is declared here as a @Bean so it can be injected
 * via constructor injection throughout the application.
 *
 * Why not instantiate RestTemplate directly in each service?
 *  - @Bean instances are managed by Spring — mockable in tests
 *  - Single instance reused across services (more efficient)
 *  - Timeouts and interceptors configurable in one place
 *  - Follows Dependency Inversion Principle
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate for outbound HTTP calls.
     *
     * Used by:
     *  - YahooFinanceService: fetch stock price history
     *  - SentimentService: fetch Reddit JSON API
     *
     * Note: for production, configure timeouts here:
     *   HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
     *   factory.setConnectTimeout(5000);
     *   factory.setReadTimeout(10000);
     *   return new RestTemplate(factory);
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
