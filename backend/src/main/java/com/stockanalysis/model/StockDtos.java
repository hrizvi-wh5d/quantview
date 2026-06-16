package com.stockanalysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * StockDtos — data transfer objects for stock price history API responses.
 *
 * These are the JSON shapes sent from backend to React frontend.
 * Designed to carry everything the charts need in one response.
 */
public class StockDtos {

    /**
     * PriceHistory — the main response object for /api/stocks/history/{symbol}
     *
     * Contains:
     *  - Raw OHLCV data (timestamps + closing prices)
     *  - Stock metadata (name, currency, exchange)
     *  - Computed log returns (used by maths engine)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceHistory {
        /** Stock ticker symbol e.g. "AAPL", "TSLA", "BATS.L" */
        private String symbol;

        /** Full company name e.g. "Apple Inc." */
        private String companyName;

        /** Currency: "USD" for NASDAQ, "GBp" for FTSE */
        private String currency;

        /** Exchange: "NASDAQ", "LSE" etc. */
        private String exchange;

        /**
         * Unix timestamps in seconds for each trading day.
         * Frontend converts: new Date(timestamp * 1000)
         */
        private List<Long> timestamps;

        /**
         * Daily closing prices — aligned 1:1 with timestamps.
         * FTSE prices from Yahoo Finance are in pence (GBp) not pounds.
         */
        private List<Double> closingPrices;

        /**
         * Log returns: ln(P_t / P_{t-1})
         * Pre-computed here so the maths engine doesn't repeat work.
         * Log returns are used because they are:
         *  1. Normally distributed (required for GBM assumption)
         *  2. Time-additive (can sum them)
         *  3. More stable than simple % returns
         */
        private List<Double> logReturns;

        /** Most recent closing price */
        private Double currentPrice;

        /** Number of trading days in the history */
        private Integer dataPoints;
    }

    /**
     * StockInfo — lightweight stock metadata for the stock picker dropdown.
     * Used to populate NASDAQ and FTSE stock lists on the dashboard.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockInfo {
        private String symbol;
        private String name;
        private String market;   // "NASDAQ" or "FTSE"
        private String sector;
    }

    /**
     * StockListResponse — wraps the list of available stocks per market.
     * Returned by /api/stocks/list/{market}
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockListResponse {
        private String market;
        private List<StockInfo> stocks;
        private Integer count;
    }
}
