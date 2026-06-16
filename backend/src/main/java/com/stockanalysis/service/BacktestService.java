package com.stockanalysis.service;

import com.stockanalysis.model.BacktestDtos.*;
import com.stockanalysis.model.StockDtos.PriceHistory;
import com.stockanalysis.service.GBMMonteCarloService.MonteCarloResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * BacktestService — validates the GBM Monte Carlo model against history.
 *
 * ─── THE CORE IDEA ────────────────────────────────────────────────────────
 *
 * We have 2 years of historical prices. For each test window we:
 *
 *   1. Pick an "as-of" index i in the historical data (pretend day i is "today")
 *   2. Use ONLY logReturns[0..i] to estimate μ and σ (no future leakage)
 *   3. Run GBM forward `forecastDays` trading days from price[i]
 *   4. Compare the prediction to the REAL price[i + forecastDays]
 *      (this is ground truth because it already happened)
 *
 * ─── PREVENTING LOOKAHEAD BIAS ───────────────────────────────────────────
 *
 * The single most important rule in backtesting: the model must NEVER see
 * data from after the as-of date. We enforce this by slicing the log
 * returns list to `subList(0, asOfIndex)` before calling the GBM service —
 * the exact same `simulate()` method used by the live dashboard, just fed
 * a truncated history. This guarantees the backtest tests the real model,
 * not a simplified copy of it.
 *
 * ─── WINDOW SPACING ───────────────────────────────────────────────────────
 *
 * We space the 20 as-of dates evenly across the available history, leaving
 * room at the end for `forecastDays` of "future" ground-truth data to exist.
 *
 * ─── INTERPRETING THE CALIBRATION SCORE ──────────────────────────────────
 *
 * GBM's cone is built from the 5th and 95th percentiles of 500 simulated
 * paths — i.e. a 90% confidence interval. If the model is well-calibrated,
 * the real future price should fall inside that cone in roughly 90% of
 * backtest windows. Significantly less than 90% means the model is
 * overconfident (cone too narrow for real-world volatility spikes).
 * Significantly more than 90% means the model is underconfident (cone
 * unnecessarily wide).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private static final int NUM_WINDOWS = 20;
    private static final double TARGET_CALIBRATION = 0.90; // 5th-95th = 90% interval

    private final YahooFinanceService yahooFinanceService;
    private final GBMMonteCarloService gbmService;

    /**
     * Run a full backtest for a symbol.
     *
     * @param symbol        stock ticker e.g. "AAPL"
     * @param forecastDays  how many trading days ahead each window predicts
     * @return BacktestResult with all window results and overall calibration
     */
    public BacktestResult runBacktest(String symbol, int forecastDays) {
        log.info("Starting backtest for {} with {} day forecast horizon", symbol, forecastDays);

        PriceHistory history = yahooFinanceService.fetchPriceHistory(symbol);
        List<Double> prices = history.getClosingPrices();
        List<Long> timestamps = history.getTimestamps();
        List<Double> logReturns = history.getLogReturns();

        int n = prices.size();

        // We need at least forecastDays of "future" data after the last
        // as-of point, plus enough history before the first as-of point
        // for GBM to estimate μ and σ reliably (minimum ~60 days).
        int minHistoryDays = 60;
        int lastValidAsOfIndex = n - forecastDays - 1;
        int firstValidAsOfIndex = minHistoryDays;

        if (lastValidAsOfIndex <= firstValidAsOfIndex) {
            throw new IllegalArgumentException(
                "Not enough historical data for a " + forecastDays + "-day backtest. " +
                "Try a shorter forecast horizon."
            );
        }

        // Evenly space NUM_WINDOWS as-of indices across the valid range
        List<Integer> asOfIndices = spaceIndicesEvenly(
            firstValidAsOfIndex, lastValidAsOfIndex, NUM_WINDOWS
        );

        List<BacktestWindow> windows = new ArrayList<>();
        int withinConeCount = 0;
        double sumAbsErrorPct = 0.0;

        for (int asOfIndex : asOfIndices) {
            BacktestWindow window = runSingleWindow(
                prices, timestamps, logReturns, asOfIndex, forecastDays
            );
            windows.add(window);

            if (Boolean.TRUE.equals(window.getWithinCone())) {
                withinConeCount++;
            }
            sumAbsErrorPct += Math.abs(window.getMedianErrorPct());
        }

        double calibrationScorePct = (double) withinConeCount / windows.size() * 100.0;
        double meanAbsErrorPct = sumAbsErrorPct / windows.size() * 100.0;
        String verdict = classifyCalibration(calibrationScorePct);

        log.info("Backtest complete for {}: calibration={}%, MAPE={}%, verdict={}",
            symbol,
            String.format("%.1f", calibrationScorePct),
            String.format("%.2f", meanAbsErrorPct),
            verdict);

        return BacktestResult.builder()
                .symbol(history.getSymbol())
                .companyName(history.getCompanyName())
                .forecastDays(forecastDays)
                .totalWindows(windows.size())
                .windows(windows)
                .calibrationScorePct(calibrationScorePct)
                .meanAbsErrorPct(meanAbsErrorPct)
                .verdict(verdict)
                .build();
    }

    /**
     * Run GBM for ONE historical as-of point and compare to the real outcome.
     *
     * CRITICAL: logReturns is truncated to [0, asOfIndex) before being passed
     * to the GBM service — the model only ever sees data available at the
     * as-of date, exactly as it would have in real life.
     */
    private BacktestWindow runSingleWindow(
            List<Double> prices,
            List<Long> timestamps,
            List<Double> logReturns,
            int asOfIndex,
            int forecastDays
    ) {
        // Truncate history to simulate "we are standing at asOfIndex"
        List<Double> truncatedReturns = logReturns.subList(0, asOfIndex);
        double startPrice = prices.get(asOfIndex);

        // Run the SAME GBM service used by the live dashboard
        MonteCarloResult mc = gbmService.simulate(truncatedReturns, startPrice, forecastDays);

        // Ground truth: what actually happened forecastDays later
        int actualIndex = asOfIndex + forecastDays;
        double actualPrice = prices.get(actualIndex);

        double predictedMedian = mc.getGbmMedianPrice();
        double predictedLower = mc.getGbmLowerPrice();
        double predictedUpper = mc.getGbmUpperPrice();

        boolean withinCone = actualPrice >= predictedLower && actualPrice <= predictedUpper;
        double medianErrorPct = (actualPrice - predictedMedian) / actualPrice;

        return BacktestWindow.builder()
                .asOfTimestamp(timestamps.get(asOfIndex))
                .startPrice(startPrice)
                .predictedMedian(predictedMedian)
                .predictedLower(predictedLower)
                .predictedUpper(predictedUpper)
                .actualPrice(actualPrice)
                .withinCone(withinCone)
                .medianErrorPct(medianErrorPct)
                .build();
    }

    /**
     * Evenly space `count` indices between `start` and `end` inclusive.
     * Used to pick non-clustered as-of dates across the available history.
     */
    private List<Integer> spaceIndicesEvenly(int start, int end, int count) {
        List<Integer> indices = new ArrayList<>();
        if (count == 1) {
            indices.add(start);
            return indices;
        }
        double step = (double) (end - start) / (count - 1);
        for (int i = 0; i < count; i++) {
            indices.add(start + (int) Math.round(step * i));
        }
        return indices;
    }

    /**
     * Classify the calibration score into a human-readable verdict.
     *
     * Target is 90% (since we use a 5th-95th percentile = 90% interval).
     * We allow a tolerance band either side before calling it miscalibrated,
     * since 20 windows is a small sample and some natural variation is expected.
     */
    private String classifyCalibration(double calibrationScorePct) {
        double target = TARGET_CALIBRATION * 100.0;
        double diff = calibrationScorePct - target;

        if (Math.abs(diff) <= 10) {
            return "Well Calibrated";
        } else if (diff < -10) {
            return "Overconfident"; // cone too narrow — real prices escape it too often
        } else {
            return "Underconfident"; // cone too wide — real prices always inside it
        }
    }
}
