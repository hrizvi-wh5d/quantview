package com.stockanalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ALevelMathsService — the original A-Level comparison mode maths engine.
 *
 * Kept alongside the fintech models so users can compare approaches:
 *   A-Level mode: simple, interpretable, limited assumptions
 *   Fintech mode: sophisticated, industry-standard, more realistic
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *
 * 1. SIMPLE MOVING AVERAGE (SMA)
 *    SMA_t = (P_{t} + P_{t-1} + ... + P_{t-n+1}) / n
 *    Smooths out short-term noise to show underlying trend.
 *
 * 2. LOG-LINEAR REGRESSION
 *    Fits a straight line to ln(price) vs time (day index).
 *    Model: ln(P_t) = a + b·t
 *    Equivalent to: P_t = e^a · e^{b·t}  (exponential growth model)
 *    This is better than linear regression on raw prices because:
 *    - Stock prices can't go negative
 *    - Percentage changes are more meaningful than absolute changes
 *    - Exponential growth is a more realistic model than linear
 *
 * 3. CONFIDENCE BANDS (±1σ and ±2σ)
 *    Based on the residuals from the regression line.
 *    residual_t = ln(P_t) - (a + b·t)
 *    σ = std dev of residuals
 *    Upper ±1σ band: exp(predicted + σ)  → back-transform from log space
 *    Upper ±2σ band: exp(predicted + 2σ)
 *    Interpretation: ~68% of prices should fall within ±1σ bands (if normally distributed)
 *                    ~95% of prices should fall within ±2σ bands
 *
 * 4. R² COEFFICIENT OF DETERMINATION
 *    R² = 1 - (SS_res / SS_tot)
 *    Where:
 *      SS_res = Σ(y_i - ŷ_i)²  (sum of squared residuals)
 *      SS_tot = Σ(y_i - ȳ)²    (total sum of squares)
 *    R² = 1.0: perfect fit, all variance explained by the model
 *    R² = 0.0: model explains nothing, no better than the mean
 *    R² < 0:   model is worse than just predicting the mean (very bad)
 */
@Service
@Slf4j
public class ALevelMathsService {

    private static final int SMA_PERIOD = 20; // 20-day SMA

    /**
     * Compute 20-day Simple Moving Average.
     *
     * @param prices list of closing prices
     * @return SMA values (null for first 19 days where window not full)
     */
    public List<Double> computeSMA(List<Double> prices) {
        List<Double> sma = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            if (i < SMA_PERIOD - 1) {
                sma.add(null); // Not enough data yet
            } else {
                double sum = 0;
                for (int j = i - SMA_PERIOD + 1; j <= i; j++) {
                    sum += prices.get(j);
                }
                sma.add(sum / SMA_PERIOD);
            }
        }
        return sma;
    }

    /**
     * Fit log-linear regression and produce forecast with confidence bands.
     *
     * Process:
     *   1. Transform: y_t = ln(P_t), x_t = t (day index)
     *   2. Fit OLS regression: ŷ = a + b·x
     *   3. Compute residuals and their std dev (σ)
     *   4. Forecast future prices: P̂_T = exp(a + b·T)
     *   5. Forecast bands: exp(a + b·T ± k·σ) for k=1,2
     *
     * @param prices    historical closing prices
     * @param daysAhead number of future trading days to forecast
     * @return ALevelResult with regression, forecast, bands, R²
     */
    public ALevelResult computeRegressionForecast(List<Double> prices, int daysAhead) {
        int n = prices.size();

        // ── STEP 1: LOG TRANSFORM ─────────────────────────────────────────────
        // y_t = ln(P_t): transform to log space for linear regression
        double[] logPrices = new double[n];
        for (int i = 0; i < n; i++) {
            logPrices[i] = Math.log(prices.get(i));
        }

        // ── STEP 2: OLS REGRESSION (Ordinary Least Squares) ──────────────────
        // Fit: ln(P_t) = a + b·t
        // x values: 0, 1, 2, ..., n-1 (day indices)
        // y values: logPrices

        // Compute means
        double xMean = (n - 1) / 2.0; // Mean of 0..n-1
        double yMean = 0;
        for (double lp : logPrices) yMean += lp;
        yMean /= n;

        // Compute slope b = Σ(x_i - x̄)(y_i - ȳ) / Σ(x_i - x̄)²
        double numerator = 0;
        double denominator = 0;
        for (int i = 0; i < n; i++) {
            double xDev = i - xMean;
            numerator   += xDev * (logPrices[i] - yMean);
            denominator += xDev * xDev;
        }
        double b = denominator != 0 ? numerator / denominator : 0; // slope
        double a = yMean - b * xMean;                               // intercept

        // ── STEP 3: COMPUTE RESIDUALS AND σ ──────────────────────────────────
        // residual_t = actual_log_price - predicted_log_price
        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) {
            residuals[i] = logPrices[i] - (a + b * i);
        }

        // Standard deviation of residuals
        double resMean = 0;
        for (double r : residuals) resMean += r;
        resMean /= n;

        double resVariance = 0;
        for (double r : residuals) {
            resVariance += (r - resMean) * (r - resMean);
        }
        resVariance /= n;
        double sigma = Math.sqrt(resVariance);

        // ── STEP 4: COMPUTE R² ────────────────────────────────────────────────
        // R² = 1 - SS_residuals / SS_total
        double ssTot = 0;
        for (double lp : logPrices) {
            ssTot += (lp - yMean) * (lp - yMean);
        }
        double ssRes = 0;
        for (double r : residuals) {
            ssRes += r * r;
        }
        double rSquared = ssTot > 0 ? 1 - (ssRes / ssTot) : 0;

        log.info("Log-linear regression: a={}, b={}, σ={}, R²={}",
            String.format("%.4f", a),
            String.format("%.6f", b),
            String.format("%.4f", sigma),
            String.format("%.4f", rSquared));

        // ── STEP 5: GENERATE FORECAST ─────────────────────────────────────────
        // For each future day (t = n, n+1, ..., n+daysAhead-1):
        //   Predicted log price: ŷ_t = a + b·t
        //   Predicted price: exp(ŷ_t)
        //   ±1σ bands: exp(ŷ_t ± σ)
        //   ±2σ bands: exp(ŷ_t ± 2σ)

        List<Double> regressionLine     = new ArrayList<>();
        List<Double> upper1Sigma        = new ArrayList<>();
        List<Double> lower1Sigma        = new ArrayList<>();
        List<Double> upper2Sigma        = new ArrayList<>();
        List<Double> lower2Sigma        = new ArrayList<>();

        for (int i = 0; i < daysAhead; i++) {
            int t = n + i; // Future day index
            double predictedLog = a + b * t;
            double predicted = Math.exp(predictedLog);

            regressionLine.add(predicted);
            upper1Sigma.add(Math.exp(predictedLog + sigma));
            lower1Sigma.add(Math.exp(predictedLog - sigma));
            upper2Sigma.add(Math.exp(predictedLog + 2 * sigma));
            lower2Sigma.add(Math.exp(predictedLog - sigma * 2));
        }

        // Target date prediction (last forecast point)
        double regressionPrediction = regressionLine.get(regressionLine.size() - 1);

        return ALevelResult.builder()
                .regressionLine(regressionLine)
                .upperBand1Sigma(upper1Sigma)
                .lowerBand1Sigma(lower1Sigma)
                .upperBand2Sigma(upper2Sigma)
                .lowerBand2Sigma(lower2Sigma)
                .rSquared(rSquared)
                .regressionPrediction(regressionPrediction)
                .slope(b)
                .intercept(a)
                .residualSigma(sigma)
                .build();
    }

    // ── RESULT OBJECT ─────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ALevelResult {
        private List<Double> regressionLine;
        private List<Double> upperBand1Sigma;
        private List<Double> lowerBand1Sigma;
        private List<Double> upperBand2Sigma;
        private List<Double> lowerBand2Sigma;
        private Double rSquared;
        private Double regressionPrediction;
        private Double slope;
        private Double intercept;
        private Double residualSigma;
    }
}
