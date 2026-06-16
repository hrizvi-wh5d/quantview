package com.stockanalysis.controller;

import com.stockanalysis.model.StockDtos.*;
import com.stockanalysis.service.StockListService;
import com.stockanalysis.service.YahooFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * StockController — REST endpoints for stock data.
 *
 * All endpoints require a valid JWT token (enforced by SecurityConfig).
 * The React frontend sends: Authorization: Bearer <token>
 *
 * Endpoints:
 *   GET /api/stocks/list/{market}     — get list of stocks for NASDAQ or FTSE
 *   GET /api/stocks/history/{symbol}  — get 2 years of daily prices
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockController {

    private final YahooFinanceService yahooFinanceService;
    private final StockListService stockListService;

    /**
     * GET /api/stocks/list/{market}
     *
     * Returns curated list of stocks for the given market.
     * Used to populate the stock picker dropdown on the dashboard.
     *
     * @param market "NASDAQ" or "FTSE"
     * @return StockListResponse with array of {symbol, name, sector}
     *
     * Example: GET /api/stocks/list/NASDAQ
     * Response: { "market": "NASDAQ", "stocks": [...], "count": 30 }
     */
    @GetMapping("/list/{market}")
    public ResponseEntity<?> getStockList(@PathVariable String market) {
        log.info("Stock list requested for market: {}", market);

        if (!market.equalsIgnoreCase("NASDAQ") && !market.equalsIgnoreCase("FTSE")) {
            return ResponseEntity.badRequest()
                    .body("Invalid market. Must be 'NASDAQ' or 'FTSE'");
        }

        StockListResponse stockList = stockListService.getStockList(market);
        return ResponseEntity.ok(stockList);
    }

    /**
     * GET /api/stocks/history/{symbol}
     *
     * Fetches 2 years of daily closing prices from Yahoo Finance.
     * Also returns pre-computed log returns for the maths engine.
     *
     * @param symbol Yahoo Finance ticker e.g. "AAPL", "BATS.L"
     * @return PriceHistory with timestamps, prices, log returns, metadata
     *
     * Example: GET /api/stocks/history/AAPL
     * Response: {
     *   "symbol": "AAPL",
     *   "companyName": "Apple Inc.",
     *   "currency": "USD",
     *   "timestamps": [1609459200, ...],
     *   "closingPrices": [132.69, ...],
     *   "logReturns": [0.0023, ...],
     *   "currentPrice": 189.30,
     *   "dataPoints": 504
     * }
     */
    @GetMapping("/history/{symbol}")
    public ResponseEntity<?> getPriceHistory(@PathVariable String symbol) {
        log.info("Price history requested for symbol: {}", symbol);

        // Validate symbol — basic sanitisation
        if (symbol == null || symbol.trim().isEmpty() || symbol.length() > 10) {
            return ResponseEntity.badRequest()
                    .body("Invalid symbol: " + symbol);
        }

        try {
            PriceHistory history = yahooFinanceService.fetchPriceHistory(symbol.trim());
            return ResponseEntity.ok(history);
        } catch (RuntimeException e) {
            log.error("Error fetching history for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(502)
                    .body("Failed to fetch data for " + symbol + ": " + e.getMessage());
        }
    }
}
