package com.stockanalysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BacktestDtos — data shapes for model validation / backtesting.
 *
 * ─── WHAT IS BACKTESTING? ─────────────────────────────────────────────────
 *
 * A backtest answers: "If I had trusted this model's predictions in the
 * past, how often would it have been right?"
 *
 * Process for ONE window:
 *  1. Pick a historical date T in the past (the "as-of" date)
 *  2. Use only price data up to T (pretend nothing after T exists yet)
 *  3. Run GBM Monte Carlo forecasting forward N days from T
 *  4. Look up the REAL price that actually occurred N days after T
 *     (we know this because T is in the past)
 *  5. Check: did the real price fall inside the model's 5th-95th
 *     percentile cone? Was the median prediction close?
 *
 * Repeating this across MANY non-overlapping windows (e.g. 20) and
 * averaging the results tells us whether the model is well-calibrated:
 * a correctly calibrated 90% confidence interval should contain the
 * actual outcome roughly 90% of the time — not 100%, not 50%.
 */
public class BacktestDtos {

    /**
     * BacktestRequest — sent from frontend to trigger a backtest.
     * POST /api/backtest/run
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BacktestRequest {
        /** Stock ticker e.g. "AAPL" */
        private String symbol;
        /** How many trading days ahead each window forecasts */
        private Integer forecastDays;
    }

    /**
     * BacktestWindow — the result of testing the model at ONE historical point.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BacktestWindow {
        /** Unix timestamp of the "as-of" date this window started from */
        private Long asOfTimestamp;
        /** Price at the as-of date (start of forecast) */
        private Double startPrice;
        /** What GBM predicted as the median price N days later */
        private Double predictedMedian;
        /** GBM's 5th percentile (worst case) prediction */
        private Double predictedLower;
        /** GBM's 95th percentile (best case) prediction */
        private Double predictedUpper;
        /** What the price ACTUALLY was N days later (ground truth) */
        private Double actualPrice;
        /**
         * Did the actual price fall inside the predicted 5th-95th cone?
         * This is the key calibration check.
         */
        private Boolean withinCone;
        /**
         * Percentage error of the median prediction vs actual:
         * (actual - predictedMedian) / actual
         */
        private Double medianErrorPct;
    }

    /**
     * BacktestResult — the full validation report across all windows.
     * Returned by POST /api/backtest/run
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BacktestResult {
        private String symbol;
        private String companyName;
        private Integer forecastDays;
        private Integer totalWindows;

        /** Individual results for each historical window tested */
        private List<BacktestWindow> windows;

        /**
         * Calibration score: % of windows where actual price fell
         * within the 5th-95th percentile cone.
         * A well-calibrated model should score close to 90%.
         */
        private Double calibrationScorePct;

        /**
         * Mean absolute percentage error (MAPE) of the median prediction
         * across all windows. Lower is better.
         */
        private Double meanAbsErrorPct;

        /**
         * Human-readable verdict based on calibration score:
         * "Well Calibrated" (80-100%), "Overconfident" (<80%),
         * "Underconfident" (cone too wide, rare but possible)
         */
        private String verdict;
    }
}
