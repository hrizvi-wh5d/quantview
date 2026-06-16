package com.stockanalysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StockAnalysisApplication — entry point for the Spring Boot backend.
 *
 * Architecture overview:
 *   - Stateless JWT authentication via Spring Security
 *   - H2 in-memory database for user persistence
 *   - Yahoo Finance v8 API for historical price data
 *   - Fintech maths engine: GBM Monte Carlo, GARCH(1,1), VaR, Sharpe, Bollinger Bands
 *   - Free sentiment analysis: Yahoo Finance RSS, Google News RSS, Reddit JSON
 */
@SpringBootApplication
@Slf4j
public class StockAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockAnalysisApplication.class, args);
        log.info("========================================");
        log.info("  QuantView Stock Analysis Backend STARTED");
        log.info("  API:        http://localhost:8080/api");
        log.info("  H2 Console: http://localhost:8080/h2-console");
        log.info("  DB URL:     jdbc:h2:mem:stockdb");
        log.info("========================================");
    }
}
