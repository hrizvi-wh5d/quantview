package com.stockanalysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FintechDtos — data transfer objects for all fintech model outputs.
 *
 * One FintechAnalysisResult bundles everything the React dashboard needs:
 *  - GBM Monte Carlo paths (median, percentile bands)
 *  - GARCH(1,1) volatility series
 *  - Value at Risk (VaR 95% and 99%)
 *  - Sharpe Ratio
 *  - Bollinger Bands
 *  - A-Level comparison (SMA, regression, confidence bands)
 */
public class FintechDtos {

    /**
     * FintechAnalysisRequest — sent from React frontend to trigger analysis.
     * POST /api/analysis/fintech
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FintechAnalysisRequest {
        /** Stock ticker e.g. "AAPL" */
        private String symbol;
        /** How many trading days ahead to forecast */
        private Integer daysAhead;
        /** Analysis mode: "FINTECH" or "ALEVEL" */
        private String mode;
    }

    /**
     * FintechAnalysisResult — the complete analysis response.
     * Contains all model outputs needed by both Fintech and A-Level modes.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FintechAnalysisResult {

        // ── METADATA ─────────────────────────────────────────────────────────
        private String symbol;
        private String companyName;
        private Double currentPrice;
        private String currency;
        private Integer daysAhead;

        // ── GBM MONTE CARLO RESULTS ───────────────────────────────────────────
        /** Median predicted price at target date (50th percentile) */
        private Double gbmMedianPrice;
        /** Best case: 95th percentile price */
        private Double gbmUpperPrice;
        /** Worst case: 5th percentile price */
        private Double gbmLowerPrice;
        /** Drift μ: mean of daily log returns (annualised) */
        private Double drift;
        /** Volatility σ: std dev of daily log returns (annualised) */
        private Double volatility;

        /**
         * Median GBM path: one price per future trading day.
         * Used to draw the central blue line on the chart.
         * Length = daysAhead
         */
        private List<Double> medianPath;

        /**
         * Upper band path (95th percentile across simulations).
         * Used for the shaded cone upper edge.
         */
        private List<Double> upperBandPath;

        /**
         * Lower band path (5th percentile across simulations).
         * Used for the shaded cone lower edge.
         */
        private List<Double> lowerBandPath;

        /**
         * Future timestamps (Unix seconds) for each forecast day.
         * Aligned 1:1 with medianPath, upperBandPath, lowerBandPath.
         */
        private List<Long> forecastTimestamps;

        // ── GARCH(1,1) VOLATILITY ─────────────────────────────────────────────
        /**
         * GARCH conditional volatility series (daily std dev).
         * Same length as historical prices (minus burn-in).
         * Used to show how volatility changes over time.
         */
        private List<Double> garchVolatilitySeries;
        /** Annualised GARCH volatility: σ_daily × √252 */
        private Double garchAnnualisedVolatility;
        /** Current GARCH volatility (most recent estimate) */
        private Double garchCurrentVolatility;

        // ── VALUE AT RISK ─────────────────────────────────────────────────────
        /** VaR at 95% confidence: 5th percentile of simulated returns */
        private Double var95Pct;       // as percentage e.g. -0.032 = -3.2%
        private Double var95Abs;       // as absolute £/$ value
        /** VaR at 99% confidence: 1st percentile of simulated returns */
        private Double var99Pct;
        private Double var99Abs;

        // ── SHARPE RATIO ──────────────────────────────────────────────────────
        /** Annualised Sharpe Ratio = (mean_return - rf) / σ × √252 */
        private Double sharpeRatio;
        /** Label: "Poor" (<1), "Good" (1-2), "Excellent" (2-3), "Outstanding" (>3) */
        private String sharpeLabel;

        // ── BOLLINGER BANDS ───────────────────────────────────────────────────
        /** 20-day Simple Moving Average series */
        private List<Double> bollingerMiddle;
        /** Upper band: SMA + 2σ */
        private List<Double> bollingerUpper;
        /** Lower band: SMA - 2σ */
        private List<Double> bollingerLower;
        /**
         * Signal: "OVERBOUGHT" if price > upper band,
         *         "OVERSOLD" if price < lower band,
         *         "NEUTRAL" otherwise
         */
        private String bollingerSignal;

        // ── A-LEVEL MODE ──────────────────────────────────────────────────────
        /** Simple Moving Average (20-day) — last N values */
        private List<Double> sma20;
        /** Log-linear regression predicted prices for forecast period */
        private List<Double> regressionLine;
        /** Upper +1σ confidence band */
        private List<Double> upperBand1Sigma;
        /** Lower -1σ confidence band */
        private List<Double> lowerBand1Sigma;
        /** Upper +2σ confidence band */
        private List<Double> upperBand2Sigma;
        /** Lower -2σ confidence band */
        private List<Double> lowerBand2Sigma;
        /** R² coefficient of determination (0 to 1) */
        private Double rSquared;
        /** Regression predicted price at target date */
        private Double regressionPrediction;

        // ── SENTIMENT ─────────────────────────────────────────────────────────
        /** Overall sentiment score from news analysis (-1 to +1) */
        private Double sentimentScore;
        /** "Bullish", "Bearish", or "Neutral" */
        private String sentimentLabel;
        /** GBM median adjusted by sentiment: median × (1 + 0.05 × score) */
        private Double sentimentAdjustedPrice;

        // ── HISTORICAL DATA (for chart) ───────────────────────────────────────
        /** Historical closing prices (last 252 days for chart clarity) */
        private List<Double> historicalPrices;
        /** Historical timestamps aligned with historicalPrices */
        private List<Long> historicalTimestamps;
    }
}
