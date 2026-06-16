package com.stockanalysis.controller;

import com.stockanalysis.model.BacktestDtos.*;
import com.stockanalysis.service.BacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * BacktestController — validates GBM model accuracy against history.
 *
 * Endpoint:
 *   POST /api/backtest/run
 *
 * Runs 20 historical test windows and reports:
 *  - calibration score (% of windows where actual price fell inside the cone)
 *  - mean absolute error of the median prediction
 *  - a verdict: "Well Calibrated", "Overconfident", or "Underconfident"
 *
 * This is a model VALIDATION tool, separate from the live forecast dashboard.
 * It answers: "would I have trusted this model historically?"
 */
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;

    /**
     * POST /api/backtest/run
     *
     * @param request { symbol, forecastDays }
     * @return BacktestResult with all 20 window results and overall calibration
     */
    @PostMapping("/run")
    public ResponseEntity<?> runBacktest(@RequestBody BacktestRequest request) {
        log.info("Backtest requested: symbol={}, forecastDays={}",
            request.getSymbol(), request.getForecastDays());

        if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Symbol is required");
        }

        int forecastDays = request.getForecastDays() != null
            ? request.getForecastDays()
            : 30; // Default to 30 trading days

        // Clamp to a sensible range — too short is noisy, too long leaves
        // too few valid historical windows in 2 years of data
        forecastDays = Math.max(5, Math.min(forecastDays, 180));

        try {
            BacktestResult result = backtestService.runBacktest(
                request.getSymbol().trim().toUpperCase(),
                forecastDays
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Backtest validation error for {}: {}", request.getSymbol(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Backtest failed for {}: {}", request.getSymbol(), e.getMessage());
            return ResponseEntity.status(502)
                    .body("Backtest failed: " + e.getMessage());
        }
    }
}
