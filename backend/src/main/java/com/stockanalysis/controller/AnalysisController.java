package com.stockanalysis.controller;

import com.stockanalysis.model.FintechDtos.*;
import com.stockanalysis.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AnalysisController — triggers full fintech analysis for a stock.
 *
 * Endpoint:
 *   POST /api/analysis/run
 *
 * The React frontend calls this when the user clicks "Analyse"
 * after selecting a stock and target date.
 *
 * This is a POST (not GET) because the request includes a body
 * with parameters (symbol, daysAhead, mode).
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * POST /api/analysis/run
     *
     * Triggers the complete analysis pipeline:
     *  1. Fetch 2 years of Yahoo Finance data
     *  2. Run GBM Monte Carlo (500 paths)
     *  3. Fit GARCH(1,1) volatility
     *  4. Compute VaR, Sharpe Ratio, Bollinger Bands
     *  5. Run A-Level regression (for comparison mode)
     *
     * @param request { symbol, daysAhead, mode, sentimentScore }
     * @return FintechAnalysisResult with all model outputs
     *
     * Typical response time: 2-5 seconds (Monte Carlo is CPU-intensive)
     */
    @PostMapping("/run")
    public ResponseEntity<?> runAnalysis(@RequestBody FintechAnalysisRequest request) {
        log.info("Analysis requested: symbol={}, daysAhead={}, mode={}",
            request.getSymbol(), request.getDaysAhead(), request.getMode());

        // Validate inputs
        if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Symbol is required");
        }

        int daysAhead = request.getDaysAhead() != null
            ? request.getDaysAhead()
            : 30; // Default to 30 trading days

        // Clamp daysAhead to sensible range
        // GBM accuracy degrades significantly beyond ~1 year
        daysAhead = Math.max(1, Math.min(daysAhead, 504));

        try {
            FintechAnalysisResult result = analysisService.analyse(
                request.getSymbol().trim().toUpperCase(),
                daysAhead
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Analysis failed for {}: {}", request.getSymbol(), e.getMessage());
            return ResponseEntity.status(502)
                    .body("Analysis failed: " + e.getMessage());
        }
    }
}
