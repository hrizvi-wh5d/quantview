package com.stockanalysis.controller;

import com.stockanalysis.model.SentimentDtos.SentimentResult;
import com.stockanalysis.service.SentimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SentimentController — fetches and scores news sentiment for a stock.
 *
 * Endpoint:
 *   GET /api/sentiment/{symbol}
 *
 * Called by the React frontend when a stock is selected.
 * Returns headlines + scores from Yahoo Finance RSS, Google News RSS, Reddit.
 *
 * The sentiment score is also used by the analysis endpoint
 * to adjust the GBM median prediction.
 */
@RestController
@RequestMapping("/api/sentiment")
@RequiredArgsConstructor
@Slf4j
public class SentimentController {

    private final SentimentService sentimentService;

    /**
     * GET /api/sentiment/{symbol}
     *
     * Fetches headlines from 3 free sources and scores them.
     * Gracefully handles source failures — always returns a result.
     *
     * @param symbol stock ticker e.g. "AAPL", "BATS.L"
     * @return SentimentResult with headlines, scores, and overall label
     *
     * Example response:
     * {
     *   "symbol": "AAPL",
     *   "overallScore": 0.15,
     *   "overallLabel": "Bullish",
     *   "headlines": [
     *     {
     *       "headline": "Apple surges after record earnings beat",
     *       "source": "Yahoo Finance",
     *       "score": 0.3,
     *       "label": "Bullish",
     *       "publishedAt": "2024-02-01"
     *     },
     *     ...
     *   ],
     *   "headlineCount": 28,
     *   "yahooCount": 10,
     *   "googleCount": 10,
     *   "redditCount": 8
     * }
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<?> getSentiment(@PathVariable String symbol) {
        log.info("Sentiment requested for: {}", symbol);

        if (symbol == null || symbol.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Symbol is required");
        }

        try {
            SentimentResult result = sentimentService.fetchAndScoreSentiment(
                symbol.trim().toUpperCase()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Sentiment fetch failed for {}: {}", symbol, e.getMessage());
            // Return neutral sentiment rather than failing — analysis can still proceed
            return ResponseEntity.ok(SentimentResult.builder()
                    .symbol(symbol.toUpperCase())
                    .overallScore(0.0)
                    .overallLabel("Neutral")
                    .headlines(java.util.Collections.emptyList())
                    .headlineCount(0)
                    .yahooCount(0)
                    .googleCount(0)
                    .redditCount(0)
                    .build());
        }
    }
}
