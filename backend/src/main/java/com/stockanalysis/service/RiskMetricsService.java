package com.stockanalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RiskMetricsService — computes Sharpe Ratio and Bollinger Bands.
 *
 * ─── SHARPE RATIO ────────────────────────────────────────────────────────────
 *
 * Developed by William Sharpe (1966), Nobel Prize in Economics (1990).
 *
 * The Sharpe Ratio measures risk-adjusted return:
 *   "How much excess return do I earn per unit of risk taken?"
 *
 * Formula:
 *   Sharpe = (R_p - R_f) / σ_p × √252
 *
 * Where:
 *   R_p  = portfolio (stock) mean daily return
 *   R_f  = risk-free rate per day (e.g. UK/US base rate / 252)
 *   σ_p  = portfolio daily return standard deviation
 *   √252 = annualisation factor (252 trading days per year)
 *
 * Risk-free rate: we use 5% annual / 252 = 0.000198 per day.
 * This approximates UK/US government bond yields as of 2024.
 *
 * Interpretation:
 *   < 1.0   = Poor         (insufficient return for risk taken)
 *   1.0-2.0 = Good         (acceptable risk-adjusted performance)
 *   2.0-3.0 = Excellent    (strong risk-adjusted performance)
 *   > 3.0   = Outstanding  (exceptional — rare in practice, suspect in backtests)
 *
 * Hedge funds typically target Sharpe > 1.5.
 * Warren Buffett's Berkshire Hathaway: historically ~0.76 Sharpe.
 * Renaissance Medallion Fund: rumoured ~2.0+ Sharpe (top secret).
 *
 * ─── BOLLINGER BANDS ─────────────────────────────────────────────────────────
 *
 * Developed by John Bollinger in the 1980s. Widely used in technical analysis.
 *
 * Definition:
 *   Middle Band = 20-day Simple Moving Average (SMA)
 *   Upper Band  = SMA + 2 × rolling standard deviation
 *   Lower Band  = SMA - 2 × rolling standard deviation
 *
 * The bands dynamically widen during volatile periods and narrow during calm ones.
 *
 * Statistical interpretation:
 *   If returns are normally distributed, ~95% of prices should fall within ±2σ.
 *   Price touching/crossing upper band → statistically "expensive" (overbought signal).
 *   Price touching/crossing lower band → statistically "cheap" (oversold signal).
 *
 * IMPORTANT CAVEAT: Bollinger Bands are descriptive, not predictive.
 * Price can "walk the band" during strong trends — overbought can stay overbought.
 * Always combine with other signals in practice.
 */
@Service
@Slf4j
public class RiskMetricsService {

    // Risk-free rate: 5% annual ÷ 252 trading days
    private static final double RISK_FREE_RATE_ANNUAL = 0.05;

    // Bollinger Band parameters
    private static final int BB_PERIOD = 20;        // 20-day lookback window
    private static final double BB_MULTIPLIER = 2.0; // ±2 standard deviations

    @Value("${fintech.trading-days-per-year:252}")
    private int tradingDaysPerYear;

    // ── SHARPE RATIO ──────────────────────────────────────────────────────────

    /**
     * Calculate the annualised Sharpe Ratio from historical log returns.
     *
     * @param logReturns daily log returns: r_t = ln(P_t / P_{t-1})
     * @return SharpeResult with ratio value and descriptive label
     */
    public SharpeResult computeSharpeRatio(List<Double> logReturns) {
        if (logReturns.isEmpty()) {
            return new SharpeResult(0.0, "Insufficient Data");
        }

        // Daily risk-free rate
        double rfDaily = RISK_FREE_RATE_ANNUAL / tradingDaysPerYear;

        // Mean daily log return
        double meanReturn = logReturns.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Daily standard deviation of returns
        double variance = logReturns.stream()
                .mapToDouble(r -> (r - meanReturn) * (r - meanReturn))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) {
            return new SharpeResult(0.0, "Insufficient Variation");
        }

        // Annualised Sharpe Ratio
        // = (mean_daily_return - daily_rf) / daily_std_dev × √252
        double sharpe = ((meanReturn - rfDaily) / stdDev) * Math.sqrt(tradingDaysPerYear);

        String label = classifySharpe(sharpe);
        log.info("Sharpe Ratio: {} ({})", String.format("%.3f", sharpe), label);

        return new SharpeResult(sharpe, label);
    }

    /**
     * Classify Sharpe Ratio into industry-standard labels.
     */
    private String classifySharpe(double sharpe) {
        if (sharpe > 3.0)      return "Outstanding";
        else if (sharpe > 2.0) return "Excellent";
        else if (sharpe > 1.0) return "Good";
        else                   return "Poor";
    }

    // ── BOLLINGER BANDS ───────────────────────────────────────────────────────

    /**
     * Compute Bollinger Bands for a price series.
     *
     * For each day t (starting at day 20), compute:
     *   SMA_t      = mean(P_{t-19} ... P_t)
     *   StdDev_t   = std_dev(P_{t-19} ... P_t)
     *   Upper_t    = SMA_t + 2 × StdDev_t
     *   Lower_t    = SMA_t - 2 × StdDev_t
     *
     * The first BB_PERIOD-1 values are not computed (no prior window).
     * We pad the output with the first valid value for alignment.
     *
     * @param prices list of closing prices
     * @return BollingerResult with middle, upper, lower bands and signal
     */
    public BollingerResult computeBollingerBands(List<Double> prices) {
        int n = prices.size();
        List<Double> middle = new ArrayList<>();
        List<Double> upper  = new ArrayList<>();
        List<Double> lower  = new ArrayList<>();

        // For the first BB_PERIOD-1 days we can't compute a full window.
        // Pad with the first valid value to keep arrays same length as prices.
        for (int i = 0; i < n; i++) {
            if (i < BB_PERIOD - 1) {
                // Not enough history yet — use null/0 (frontend will skip these)
                middle.add(null);
                upper.add(null);
                lower.add(null);
                continue;
            }

            // Extract the window: prices[i-19] to prices[i] (20 values)
            List<Double> window = prices.subList(i - BB_PERIOD + 1, i + 1);

            // SMA: arithmetic mean of window
            double sma = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            // Rolling standard deviation of the window
            double windowVariance = window.stream()
                    .mapToDouble(p -> (p - sma) * (p - sma))
                    .average()
                    .orElse(0.0);
            double windowStdDev = Math.sqrt(windowVariance);

            middle.add(sma);
            upper.add(sma + BB_MULTIPLIER * windowStdDev);
            lower.add(sma - BB_MULTIPLIER * windowStdDev);
        }

        // ── BOLLINGER SIGNAL ──────────────────────────────────────────────────
        // Based on the MOST RECENT price vs most recent bands
        String signal = "NEUTRAL";
        double lastPrice = prices.get(n - 1);

        // Find the most recent non-null band values
        Double lastUpper = null;
        Double lastLower = null;
        for (int i = upper.size() - 1; i >= 0; i--) {
            if (upper.get(i) != null) { lastUpper = upper.get(i); break; }
        }
        for (int i = lower.size() - 1; i >= 0; i--) {
            if (lower.get(i) != null) { lastLower = lower.get(i); break; }
        }

        if (lastUpper != null && lastLower != null) {
            if (lastPrice > lastUpper) {
                signal = "OVERBOUGHT"; // Price above upper band: potentially expensive
            } else if (lastPrice < lastLower) {
                signal = "OVERSOLD";  // Price below lower band: potentially cheap
            }
        }

        log.info("Bollinger Bands computed: signal={}, last price={}", signal, String.format("%.2f", lastPrice));

        return new BollingerResult(middle, upper, lower, signal);
    }

    // ── RESULT OBJECTS ────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SharpeResult {
        private Double sharpeRatio;
        private String label;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BollingerResult {
        private List<Double> middle;
        private List<Double> upper;
        private List<Double> lower;
        private String signal;
    }
}
