package com.stockanalysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SentimentDtos — data transfer objects for sentiment analysis results.
 *
 * The sentiment engine fetches headlines from 3 free sources:
 *  1. Yahoo Finance RSS
 *  2. Google News RSS
 *  3. Reddit r/wallstreetbets JSON API
 *
 * Each headline is scored using a VADER-equivalent word list approach.
 * The overall score adjusts the GBM median prediction.
 */
public class SentimentDtos {

    /**
     * SentimentHeadline — a single news headline with its sentiment score.
     * Displayed in the Sentiment Panel on the dashboard.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentHeadline {
        /** The headline text */
        private String headline;

        /** Source: "Yahoo Finance", "Google News", "Reddit WSB" */
        private String source;

        /**
         * Sentiment score for this headline: -1.0 to +1.0
         * Computed as: +0.1 per positive word, -0.1 per negative word, clamped to [-1, +1]
         */
        private Double score;

        /**
         * Label for this headline: "Bullish", "Bearish", "Neutral"
         * Based on the score threshold (>0.05 = Bullish, <-0.05 = Bearish)
         */
        private String label;

        /** Publication date/time as string for display */
        private String publishedAt;

        /** URL to the original article */
        private String url;
    }

    /**
     * SentimentResult — aggregated sentiment analysis for a stock.
     * Returned by POST /api/sentiment/{symbol}
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentResult {
        /** Stock ticker */
        private String symbol;

        /**
         * Overall sentiment score: average of all headline scores.
         * Range: -1.0 (extremely bearish) to +1.0 (extremely bullish)
         */
        private Double overallScore;

        /**
         * Overall label: "Bullish" (>0.05), "Bearish" (<-0.05), "Neutral"
         */
        private String overallLabel;

        /** List of up to 10 scored headlines (from all sources combined) */
        private List<SentimentHeadline> headlines;

        /** Number of headlines analysed */
        private Integer headlineCount;

        /** Breakdown by source */
        private Integer yahooCount;
        private Integer googleCount;
        private Integer redditCount;

        /**
         * Sentiment-adjusted GBM prediction.
         * = gbmMedianPrice × (1 + 0.05 × overallScore)
         * Populated by AnalysisService after combining with GBM result.
         */
        private Double sentimentAdjustedPrice;
    }
}
