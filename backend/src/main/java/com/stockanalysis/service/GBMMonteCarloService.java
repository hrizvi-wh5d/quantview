package com.stockanalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GBMMonteCarloService — Geometric Brownian Motion Monte Carlo simulation.
 *
 * ─── FINANCE THEORY ──────────────────────────────────────────────────────────
 *
 * GBM is the foundational model for stock price dynamics.
 * It underpins the Black-Scholes options pricing formula (Nobel Prize 1997).
 *
 * The stochastic differential equation (SDE):
 *   dS = μS·dt + σS·dW
 *
 * Where:
 *   S  = stock price
 *   μ  = drift (expected return per unit time)
 *   σ  = volatility (std dev of returns per unit time)
 *   dW = Wiener process increment (Brownian motion) = Z·√dt, Z ~ N(0,1)
 *
 * The exact discretised solution (Euler-Maruyama):
 *   S(t+dt) = S(t) · exp((μ - σ²/2)·dt + σ·√dt·Z)
 *
 * The (μ - σ²/2) term is the "Itô correction" — without it the simulation
 * would have an upward bias because of Jensen's inequality applied to exp().
 *
 * ─── IMPLEMENTATION ───────────────────────────────────────────────────────────
 *
 * We run 500 independent simulated price paths.
 * Each path starts at the current price and steps forward one trading day at a time.
 * At each time step we draw Z ~ N(0,1) independently for each path.
 *
 * At each future time step we collect all 500 prices and compute percentiles.
 * We return the 5th, 50th, and 95th percentile paths (not all 500 raw paths)
 * to keep the JSON response size manageable.
 *
 * ─── PARAMETERS ──────────────────────────────────────────────────────────────
 *   μ (drift)     = mean of historical daily log returns
 *   σ (volatility) = std dev of historical daily log returns
 *   dt = 1/252     (one trading day as a fraction of a year)
 *
 * ─── PERFORMANCE NOTE ────────────────────────────────────────────────────────
 * Use ThreadLocalRandom (not Math.random()) for Monte Carlo.
 * ThreadLocalRandom is designed for high-frequency random number generation
 * and avoids contention that occurs with a shared Random instance.
 */
@Service
@Slf4j
public class GBMMonteCarloService {

    @Value("${fintech.monte-carlo.paths:500}")
    private int numPaths;

    @Value("${fintech.trading-days-per-year:252}")
    private int tradingDaysPerYear;

    /**
     * Run GBM Monte Carlo simulation.
     *
     * @param logReturns    historical daily log returns
     * @param currentPrice  most recent closing price (simulation start)
     * @param daysAhead     number of trading days to simulate forward
     * @return MonteCarloResult with percentile paths and statistics
     */
    public MonteCarloResult simulate(
            List<Double> logReturns,
            double currentPrice,
            int daysAhead
    ) {
        log.info("Running {} GBM paths for {} days ahead", numPaths, daysAhead);

        // ── STEP 1: ESTIMATE PARAMETERS FROM HISTORICAL DATA ─────────────────

        // μ (drift): mean of daily log returns
        // This is the average daily return the stock has historically achieved
        double mu = computeMean(logReturns);

        // σ (volatility): standard deviation of daily log returns
        // This measures how much the stock price fluctuates day to day
        double sigma = computeStdDev(logReturns, mu);

        // dt = 1 trading day, expressed in the SAME units as μ and σ.
        //
        // CRITICAL: μ and σ above are DAILY statistics (computed directly
        // from daily log returns) — they are NOT annualised. Each simulation
        // step below represents exactly one trading day, so dt = 1.0 here,
        // not 1/252. Dividing by tradingDaysPerYear would be correct only
        // if μ and σ were annual figures being converted down to a daily
        // step size — but they already ARE daily figures.
        //
        // (A previous version of this code incorrectly used dt = 1/252,
        // which shrank the simulated volatility by a factor of √252 ≈ 15.9,
        // making the forecast cone far too narrow. Verified via backtest:
        // with dt = 1/252, a stock with 48% annualised volatility produced
        // a 252-day cumulative cone width of only ~3% instead of ~48%.)
        double dt = 1.0;

        log.debug("GBM params: μ={}/day, σ={}/day, current={}",
            String.format("%.6f", mu),
            String.format("%.6f", sigma),
            String.format("%.2f", currentPrice));

        // ── STEP 2: RUN 500 SIMULATION PATHS ─────────────────────────────────

        // simPrices[path][day] = simulated price
        // We need all paths at each time step to compute percentiles
        double[][] simPrices = new double[numPaths][daysAhead];

        for (int path = 0; path < numPaths; path++) {
            double price = currentPrice;

            for (int day = 0; day < daysAhead; day++) {
                // Draw Z ~ N(0,1) standard normal random variable
                // ThreadLocalRandom.nextGaussian() uses Box-Muller transform
                double Z = ThreadLocalRandom.current().nextGaussian();

                // GBM discretised formula:
                // S(t+dt) = S(t) · exp((μ - σ²/2)·dt + σ·√dt·Z)
                //
                // The (μ - σ²/2) term is the Itô correction.
                // Without it, E[S(T)] would exceed S(0)·exp(μT) due to
                // Jensen's inequality: E[exp(X)] > exp(E[X]) for random X.
                double drift_term = (mu - 0.5 * sigma * sigma) * dt;
                double diffusion_term = sigma * Math.sqrt(dt) * Z;
                price = price * Math.exp(drift_term + diffusion_term);

                simPrices[path][day] = price;
            }
        }

        // ── STEP 3: COMPUTE PERCENTILE PATHS ACROSS ALL SIMULATIONS ──────────

        // For each day, collect all 500 simulated prices and sort them.
        // Then extract 5th, 50th, 95th percentiles.
        // This gives us the cone shape: narrow near today, wide in the future.

        List<Double> medianPath = new ArrayList<>();
        List<Double> upperPath = new ArrayList<>();  // 95th percentile
        List<Double> lowerPath = new ArrayList<>();  // 5th percentile

        for (int day = 0; day < daysAhead; day++) {
            // Collect all 500 prices for this day
            double[] dayPrices = new double[numPaths];
            for (int path = 0; path < numPaths; path++) {
                dayPrices[path] = simPrices[path][day];
            }
            Arrays.sort(dayPrices);

            // Extract percentiles from sorted array
            medianPath.add(percentile(dayPrices, 50));
            upperPath.add(percentile(dayPrices, 95));
            lowerPath.add(percentile(dayPrices, 5));
        }

        // ── STEP 4: COMPUTE VAR FROM FINAL DAY DISTRIBUTION ──────────────────

        // Collect final prices from all paths (last day of simulation)
        double[] finalPrices = new double[numPaths];
        for (int path = 0; path < numPaths; path++) {
            finalPrices[path] = simPrices[path][daysAhead - 1];
        }
        Arrays.sort(finalPrices);

        // Convert final prices to returns: (finalPrice - currentPrice) / currentPrice
        double[] finalReturns = new double[numPaths];
        for (int i = 0; i < numPaths; i++) {
            finalReturns[i] = (finalPrices[i] - currentPrice) / currentPrice;
        }
        Arrays.sort(finalReturns);

        // VaR(95%) = 5th percentile of return distribution
        // Interpretation: "With 95% confidence, loss won't exceed X%"
        double var95Pct = percentile(finalReturns, 5);
        double var99Pct = percentile(finalReturns, 1);

        // Annualise the parameters for reporting
        double annualisedMu = mu * tradingDaysPerYear;
        double annualisedSigma = sigma * Math.sqrt(tradingDaysPerYear);

        log.info("GBM complete: median={}, upper={}, lower={}, VaR95={}%",
            String.format("%.2f", medianPath.get(daysAhead - 1)),
            String.format("%.2f", upperPath.get(daysAhead - 1)),
            String.format("%.2f", lowerPath.get(daysAhead - 1)),
            String.format("%.2f", var95Pct * 100));

        return MonteCarloResult.builder()
                .medianPath(medianPath)
                .upperPath(upperPath)
                .lowerPath(lowerPath)
                .gbmMedianPrice(medianPath.get(daysAhead - 1))
                .gbmUpperPrice(upperPath.get(daysAhead - 1))
                .gbmLowerPrice(lowerPath.get(daysAhead - 1))
                .drift(annualisedMu)
                .volatility(annualisedSigma)
                .dailyMu(mu)
                .dailySigma(sigma)
                .var95Pct(var95Pct)
                .var99Pct(var99Pct)
                .build();
    }

    // ── HELPER METHODS ────────────────────────────────────────────────────────

    /** Arithmetic mean of a list of doubles */
    public double computeMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Population standard deviation.
     * σ = √(Σ(x - μ)² / N)
     * We use population (not sample) std dev because we're treating
     * the historical data as the true distribution parameter estimate.
     */
    public double computeStdDev(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    /**
     * Compute the p-th percentile of a SORTED array.
     * Uses linear interpolation between adjacent values.
     *
     * @param sortedArray array sorted in ascending order
     * @param p percentile to compute (0-100)
     * @return interpolated percentile value
     */
    private double percentile(double[] sortedArray, double p) {
        if (sortedArray.length == 0) return 0;
        double index = (p / 100.0) * (sortedArray.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sortedArray[lower];
        // Linear interpolation
        double fraction = index - lower;
        return sortedArray[lower] * (1 - fraction) + sortedArray[upper] * fraction;
    }

    // ── RESULT OBJECT ─────────────────────────────────────────────────────────

    /**
     * MonteCarloResult — output from the GBM simulation.
     * Passed to the analysis controller to be merged into FintechAnalysisResult.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MonteCarloResult {
        private List<Double> medianPath;
        private List<Double> upperPath;
        private List<Double> lowerPath;
        private Double gbmMedianPrice;
        private Double gbmUpperPrice;
        private Double gbmLowerPrice;
        private Double drift;           // annualised
        private Double volatility;      // annualised
        private Double dailyMu;         // raw daily drift
        private Double dailySigma;      // raw daily volatility
        private Double var95Pct;        // as decimal e.g. -0.032
        private Double var99Pct;
    }
}
