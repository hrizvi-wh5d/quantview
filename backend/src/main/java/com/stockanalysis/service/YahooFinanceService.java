package com.stockanalysis.service;

import com.stockanalysis.model.StockDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * YahooFinanceService — fetches historical stock price data from Yahoo Finance v8 API.
 *
 * Yahoo Finance v8 API endpoint:
 *   https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 *   ?interval=1d          → daily candles
 *   &range=2y             → 2 years of history
 *
 * CRITICAL implementation notes (hard-learned lessons):
 *
 * 1. USER-AGENT HEADER: Yahoo Finance returns 429 Too Many Requests without it.
 *    Always set: User-Agent: Mozilla/5.0
 *
 * 2. LIST<NUMBER> CASTING: Yahoo Finance v8 returns timestamps as integers
 *    and prices as decimals in a generic JSON array. Jackson deserialises these
 *    as List<Integer>, List<Long>, or List<Double> depending on the value size.
 *    If you cast directly to List<Long> or List<Double> you get ClassCastException.
 *    ALWAYS cast to List<Number> first, then call .longValue() / .doubleValue().
 *
 * 3. NULL HANDLING: Yahoo Finance includes null entries for market holidays
 *    and non-trading days. Filter these out before computation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YahooFinanceService {

    // Yahoo Finance v8 chart API base URL
    private static final String YAHOO_API_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/";

    // User-Agent header value — required to avoid 429 rate limiting
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    @Value("${fintech.history-years:2}")
    private int historyYears;

    private final RestTemplate restTemplate;

    /**
     * Fetch historical daily closing prices for a stock symbol.
     *
     * @param symbol Yahoo Finance ticker e.g. "AAPL", "TSLA", "BATS.L"
     * @return PriceHistory containing timestamps, prices, and log returns
     * @throws RuntimeException if Yahoo Finance returns an error
     */
    public PriceHistory fetchPriceHistory(String symbol) {
        log.info("Fetching {} years of daily data for: {}", historyYears, symbol);

        // Build URL with 2 year range and daily interval
        String url = YAHOO_API_URL + symbol.toUpperCase() +
                     "?interval=1d&range=" + historyYears + "y";

        // Build request headers — User-Agent is CRITICAL
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", "application/json");
        headers.set("Accept-Language", "en-US,en;q=0.9");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Use typed response to avoid raw Map warning
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("Yahoo Finance returned: " + response.getStatusCode());
            }

            return parseYahooResponse(symbol, response.getBody());

        } catch (Exception e) {
            log.error("Failed to fetch data for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to fetch stock data for " + symbol + ": " + e.getMessage());
        }
    }

    /**
     * Parse the Yahoo Finance v8 JSON response into our PriceHistory DTO.
     *
     * Yahoo Finance v8 JSON structure:
     * {
     *   "chart": {
     *     "result": [{
     *       "meta": { "symbol": "AAPL", "currency": "USD", ... },
     *       "timestamp": [1609459200, 1609545600, ...],   ← Unix seconds
     *       "indicators": {
     *         "quote": [{
     *           "close": [132.69, 129.41, ...],           ← closing prices
     *           "open": [...], "high": [...], "low": [...], "volume": [...]
     *         }]
     *       }
     *     }],
     *     "error": null
     *   }
     * }
     *
     * CRITICAL: Cast timestamps and prices as List<Number>, not List<Long>/List<Double>
     */
    @SuppressWarnings("unchecked")
    private PriceHistory parseYahooResponse(String symbol, Map<String, Object> body) {

        // Navigate the nested JSON structure
        Map<String, Object> chart = (Map<String, Object>) body.get("chart");

        // Check for API-level errors
        Object error = chart.get("error");
        if (error != null) {
            throw new RuntimeException("Yahoo Finance API error: " + error);
        }

        List<Object> results = (List<Object>) chart.get("result");
        if (results == null || results.isEmpty()) {
            throw new RuntimeException("No data returned for symbol: " + symbol);
        }

        Map<String, Object> result = (Map<String, Object>) results.get(0);

        // Extract metadata
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        String companyName = (String) meta.getOrDefault("shortName", symbol);
        String currency = (String) meta.getOrDefault("currency", "USD");
        String exchange = (String) meta.getOrDefault("exchangeName", "");

        // ── CRITICAL: Cast as List<Number>, NOT List<Long> ──────────────────
        // Jackson may deserialise small timestamps as Integer, large ones as Long.
        // Direct cast to List<Long> throws ClassCastException.
        List<Number> rawTimestamps = (List<Number>) result.get("timestamp");

        // Extract quote data (nested under indicators → quote → [0])
        Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
        List<Object> quoteList = (List<Object>) indicators.get("quote");
        Map<String, Object> quote = (Map<String, Object>) quoteList.get(0);

        // ── CRITICAL: Cast as List<Number>, NOT List<Double> ─────────────────
        // Yahoo Finance returns some prices as Integer (e.g. 0) and some as Double.
        // Direct cast to List<Double> throws ClassCastException.
        List<Number> rawClosePrices = (List<Number>) quote.get("close");

        if (rawTimestamps == null || rawClosePrices == null) {
            throw new RuntimeException("Missing price data for: " + symbol);
        }

        // ── FILTER AND CONVERT ────────────────────────────────────────────────
        // Yahoo includes nulls for weekends/holidays — filter these out
        List<Long> timestamps = new ArrayList<>();
        List<Double> closingPrices = new ArrayList<>();

        for (int i = 0; i < rawTimestamps.size() && i < rawClosePrices.size(); i++) {
            Number ts = rawTimestamps.get(i);
            Number price = rawClosePrices.get(i);

            // Skip null entries (market holidays, weekends)
            if (ts == null || price == null) continue;

            // Use .longValue() and .doubleValue() — safe regardless of Integer/Long/Double
            timestamps.add(ts.longValue());
            closingPrices.add(price.doubleValue());
        }

        if (closingPrices.size() < 30) {
            throw new RuntimeException("Insufficient data for " + symbol +
                " (only " + closingPrices.size() + " data points)");
        }

        // ── COMPUTE LOG RETURNS ───────────────────────────────────────────────
        // Log return: r_t = ln(P_t / P_{t-1}) = ln(P_t) - ln(P_{t-1})
        //
        // Why log returns?
        //  - Normally distributed → satisfies GBM assumption
        //  - Time-additive: sum of daily log returns = total log return
        //  - Symmetric: +10% and -10% moves have equal magnitude
        List<Double> logReturns = computeLogReturns(closingPrices);

        double currentPrice = closingPrices.get(closingPrices.size() - 1);

        log.info("Successfully fetched {} data points for {} (current price: {} {})",
            closingPrices.size(), symbol, String.format("%.2f", currentPrice), currency);

        return PriceHistory.builder()
                .symbol(symbol.toUpperCase())
                .companyName(companyName)
                .currency(currency)
                .exchange(exchange)
                .timestamps(timestamps)
                .closingPrices(closingPrices)
                .logReturns(logReturns)
                .currentPrice(currentPrice)
                .dataPoints(closingPrices.size())
                .build();
    }

    /**
     * Compute daily log returns from a price series.
     *
     * Formula: r_t = ln(P_t / P_{t-1})
     *
     * Result has one fewer element than prices
     * (can't compute return for the first day — no previous price).
     *
     * @param prices list of closing prices
     * @return list of daily log returns
     */
    private List<Double> computeLogReturns(List<Double> prices) {
        List<Double> logReturns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            double prev = prices.get(i - 1);
            double curr = prices.get(i);
            if (prev > 0 && curr > 0) {
                logReturns.add(Math.log(curr / prev));
            }
        }
        return logReturns;
    }
}
