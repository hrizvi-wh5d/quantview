package com.stockanalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * GARCHService — GARCH(1,1) Generalised Autoregressive Conditional Heteroskedasticity.
 *
 * ─── FINANCE THEORY ──────────────────────────────────────────────────────────
 *
 * Problem with simple volatility models:
 *   Naive approach: σ = std dev of all historical returns (constant).
 *   Reality: volatility CLUSTERS. Turbulent periods follow turbulent periods.
 *   "Volatility of volatility" is a real phenomenon (e.g. 2008 crash, COVID 2020).
 *
 * GARCH(1,1) — developed by Tim Bollerslev (1986), building on Engle's ARCH model.
 * Engle won the Nobel Prize in Economics (2003) for this work.
 * GARCH stands for: Generalised AutoRegressive Conditional Heteroskedasticity.
 *   - Autoregressive: today's volatility depends on yesterday's
 *   - Conditional: conditional on past information
 *   - Heteroskedasticity: variance changes over time (not constant)
 *
 * The GARCH(1,1) equation:
 *   σ²_t = ω + α·ε²_{t-1} + β·σ²_{t-1}
 *
 * Where:
 *   σ²_t       = today's conditional variance
 *   ω (omega)  = long-run variance weight (base level)
 *   α (alpha)  = weight on last period's squared shock (ε²_{t-1})
 *   ε_{t-1}    = yesterday's return shock (actual return - expected return)
 *   β (beta)   = weight on last period's conditional variance (persistence)
 *
 * The (1,1) means: 1 lag of squared errors + 1 lag of variance.
 *
 * Stability condition: α + β < 1
 *   With α=0.1, β=0.85: α + β = 0.95 < 1 ✓ (stable, mean-reverting)
 *
 * Mean reversion: long-run variance = ω / (1 - α - β)
 *   This means after a shock, volatility gradually returns to its long-run average.
 *
 * ─── PARAMETER CHOICE ────────────────────────────────────────────────────────
 *   ω = 0.000001  — very small base variance
 *   α = 0.1       — 10% weight on recent shock (news impact)
 *   β = 0.85      — 85% persistence (volatility is sticky)
 *   α + β = 0.95  — high persistence, slow mean reversion (typical for stocks)
 *
 * ─── ANNUALISATION ────────────────────────────────────────────────────────────
 *   Daily volatility (σ_daily) → Annual volatility: σ_annual = σ_daily × √252
 *   This works because variance scales linearly with time (for independent returns),
 *   so standard deviation scales with √time.
 */
@Service
@Slf4j
public class GARCHService {

    // GARCH(1,1) parameters — standard values used in industry
    private static final double OMEGA = 0.000001;  // ω: long-run variance weight
    private static final double ALPHA = 0.1;       // α: recent shock weight
    private static final double BETA  = 0.85;      // β: variance persistence

    @Value("${fintech.trading-days-per-year:252}")
    private int tradingDaysPerYear;

    /**
     * Fit GARCH(1,1) model to historical log returns and return volatility series.
     *
     * @param logReturns list of daily log returns: r_t = ln(P_t / P_{t-1})
     * @return GARCHResult with daily and annualised volatility series
     */
    public GARCHResult fitGARCH(List<Double> logReturns) {
        log.info("Fitting GARCH(1,1) to {} observations", logReturns.size());

        int n = logReturns.size();
        if (n < 10) {
            throw new IllegalArgumentException("Need at least 10 observations for GARCH");
        }

        // ── STEP 1: INITIALISE ────────────────────────────────────────────────

        // Compute mean return (μ) — used to centre the return shocks
        double meanReturn = logReturns.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Initialise σ² with the unconditional (sample) variance
        // This is the "warm-up" variance before the recursion starts
        double initialVariance = 0.0;
        for (double r : logReturns) {
            double shock = r - meanReturn;
            initialVariance += shock * shock;
        }
        initialVariance /= n;

        // ── STEP 2: GARCH RECURSION ───────────────────────────────────────────

        // Arrays to store the conditional variance and volatility series
        List<Double> varianceSeries = new ArrayList<>();
        List<Double> volatilitySeries = new ArrayList<>();

        double sigma2 = initialVariance; // Start with sample variance

        for (int t = 0; t < n; t++) {
            // ε_t = return shock = actual return - expected return (mean)
            double epsilon = logReturns.get(t) - meanReturn;
            double epsilon2 = epsilon * epsilon; // squared shock

            // GARCH(1,1) equation:
            // σ²_t = ω + α·ε²_{t-1} + β·σ²_{t-1}
            //
            // For t=0 we use the initial variance as σ²_{t-1}
            // For t>0 we use the previous iteration's σ²
            if (t > 0) {
                double prevEpsilon = logReturns.get(t - 1) - meanReturn;
                double prevEpsilon2 = prevEpsilon * prevEpsilon;
                // Update: new variance = base + (shock weight × prev shock²) + (persistence × prev variance)
                sigma2 = OMEGA + ALPHA * prevEpsilon2 + BETA * sigma2;
            }

            // Ensure variance stays positive (numerical stability)
            sigma2 = Math.max(sigma2, 1e-10);

            // σ_t = √σ²_t = daily conditional standard deviation (volatility)
            double sigma = Math.sqrt(sigma2);

            varianceSeries.add(sigma2);
            volatilitySeries.add(sigma);
        }

        // ── STEP 3: COMPUTE SUMMARY STATISTICS ───────────────────────────────

        // Current GARCH volatility = most recent estimate
        double currentDailyVol = volatilitySeries.get(volatilitySeries.size() - 1);

        // Annualise: σ_annual = σ_daily × √252
        // (Assumes returns are independent day-to-day — standard assumption)
        double currentAnnualVol = currentDailyVol * Math.sqrt(tradingDaysPerYear);

        // Long-run (unconditional) variance: ω / (1 - α - β)
        // This is what GARCH predicts volatility will mean-revert to
        double longRunVariance = OMEGA / (1 - ALPHA - BETA);
        double longRunVolatility = Math.sqrt(longRunVariance) * Math.sqrt(tradingDaysPerYear);

        log.info("GARCH fitted: current daily σ={}, annualised σ={}%, long-run σ={}%",
            String.format("%.4f", currentDailyVol),
            String.format("%.2f", currentAnnualVol * 100),
            String.format("%.2f", longRunVolatility * 100));

        return GARCHResult.builder()
                .dailyVolatilitySeries(volatilitySeries)
                .currentDailyVolatility(currentDailyVol)
                .currentAnnualisedVolatility(currentAnnualVol)
                .longRunAnnualisedVolatility(longRunVolatility)
                .omega(OMEGA)
                .alpha(ALPHA)
                .beta(BETA)
                .build();
    }

    // ── RESULT OBJECT ─────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GARCHResult {
        /** Daily conditional volatility (σ_t) for each historical day */
        private List<Double> dailyVolatilitySeries;
        /** Most recent daily volatility estimate */
        private Double currentDailyVolatility;
        /** Most recent annualised volatility: σ_daily × √252 */
        private Double currentAnnualisedVolatility;
        /** Long-run annualised volatility: √(ω/(1-α-β)) × √252 */
        private Double longRunAnnualisedVolatility;
        /** Model parameters (for transparency / debugging) */
        private Double omega;
        private Double alpha;
        private Double beta;
    }
}
