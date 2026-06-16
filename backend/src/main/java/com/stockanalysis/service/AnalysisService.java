package com.stockanalysis.service;

import com.stockanalysis.model.FintechDtos.*;
import com.stockanalysis.model.SentimentDtos.SentimentResult;
import com.stockanalysis.model.StockDtos.PriceHistory;
import com.stockanalysis.service.GBMMonteCarloService.MonteCarloResult;
import com.stockanalysis.service.GARCHService.GARCHResult;
import com.stockanalysis.service.RiskMetricsService.SharpeResult;
import com.stockanalysis.service.RiskMetricsService.BollingerResult;
import com.stockanalysis.service.ALevelMathsService.ALevelResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AnalysisService — orchestrates the full fintech analysis pipeline.
 *
 * Pipeline:
 *  1. Fetch price history from Yahoo Finance
 *  2. Run GBM Monte Carlo simulation (500 paths)
 *  3. Fit GARCH(1,1) volatility model
 *  4. Compute Value at Risk (from Monte Carlo distribution)
 *  5. Calculate Sharpe Ratio
 *  6. Compute Bollinger Bands
 *  7. Run A-Level regression model (for comparison)
 *  8. Bundle everything into FintechAnalysisResult
 *
 * This service is the "brain" — it coordinates all the individual
 * maths services and assembles the complete response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final YahooFinanceService yahooFinanceService;
    private final GBMMonteCarloService gbmService;
    private final GARCHService garchService;
    private final RiskMetricsService riskMetricsService;
    private final ALevelMathsService aLevelMathsService;
    private final SentimentService sentimentService;

    @Value("${fintech.trading-days-per-year:252}")
    private int tradingDaysPerYear;

    /**
     * Run complete fintech analysis for a stock symbol.
     *
     * @param symbol    stock ticker e.g. "AAPL", "BATS.L"
     * @param daysAhead trading days to forecast (e.g. 30, 90, 252)
     * @return FintechAnalysisResult with all model outputs
     */
    public FintechAnalysisResult analyse(
            String symbol,
            int daysAhead
    ) {
        log.info("Starting full analysis for {} ({} days ahead)", symbol, daysAhead);

        // ── STEP 1: FETCH HISTORICAL DATA ─────────────────────────────────────
        PriceHistory history = yahooFinanceService.fetchPriceHistory(symbol);
        List<Double> prices = history.getClosingPrices();
        List<Long> timestamps = history.getTimestamps();
        List<Double> logReturns = history.getLogReturns();
        double currentPrice = history.getCurrentPrice();

        // ── STEP 2: GBM MONTE CARLO ───────────────────────────────────────────
        MonteCarloResult mcResult = gbmService.simulate(logReturns, currentPrice, daysAhead);

        // ── STEP 3: GARCH(1,1) VOLATILITY ─────────────────────────────────────
        GARCHResult garchResult = garchService.fitGARCH(logReturns);

        // ── STEP 4: SHARPE RATIO ──────────────────────────────────────────────
        SharpeResult sharpeResult = riskMetricsService.computeSharpeRatio(logReturns);

        // ── STEP 5: BOLLINGER BANDS ───────────────────────────────────────────
        BollingerResult bollingerResult = riskMetricsService.computeBollingerBands(prices);

        // ── STEP 6: A-LEVEL REGRESSION ────────────────────────────────────────
        ALevelResult aLevelResult = aLevelMathsService.computeRegressionForecast(
            prices, daysAhead
        );
        List<Double> sma20 = aLevelMathsService.computeSMA(prices);

        // ── STEP 7: SENTIMENT ANALYSIS ────────────────────────────────────────
        // Fetch and score headlines from Yahoo Finance RSS, Google News, Reddit
        // sentimentScore is passed in from the controller (pre-fetched)
        // so analysis and sentiment can be called in parallel by the frontend
        SentimentResult sentiment = sentimentService.fetchAndScoreSentiment(symbol);
        double resolvedSentimentScore = sentiment.getOverallScore();
        // VaR as absolute £/$ value = VaR% × currentPrice
        double var95Abs = mcResult.getVar95Pct() * currentPrice;
        double var99Abs = mcResult.getVar99Pct() * currentPrice;

        // ── STEP 8: SENTIMENT ADJUSTMENT ─────────────────────────────────────
        // Adjust GBM median prediction by sentiment:
        // adjusted = gbm_median × (1 + 0.05 × sentiment_score)
        // e.g. sentiment=+0.8 (very bullish) → +4% adjustment
        //      sentiment=-0.6 (bearish)       → -3% adjustment
        double sentimentAdjustedPrediction = mcResult.getGbmMedianPrice()
                * (1 + 0.05 * resolvedSentimentScore);

        // ── STEP 9: GENERATE FORECAST TIMESTAMPS ─────────────────────────────
        // Create Unix timestamps (seconds) for each future trading day
        // Starting from the next trading day after the last historical date
        List<Long> forecastTimestamps = generateForecastTimestamps(
            timestamps.get(timestamps.size() - 1), daysAhead
        );

        // ── STEP 10: TRIM HISTORICAL DATA FOR CHART ───────────────────────────
        // Send last 252 days (1 year) for the chart — sending 2 years makes
        // the JSON response large and the chart cluttered
        int chartDays = Math.min(252, prices.size());
        int startIdx = prices.size() - chartDays;

        List<Double> chartPrices = prices.subList(startIdx, prices.size());
        List<Long> chartTimestamps = timestamps.subList(startIdx, timestamps.size());

        // Trim Bollinger Bands to match chart window
        List<Double> bbMiddle = trimList(bollingerResult.getMiddle(), startIdx);
        List<Double> bbUpper  = trimList(bollingerResult.getUpper(), startIdx);
        List<Double> bbLower  = trimList(bollingerResult.getLower(), startIdx);
        List<Double> sma20Trimmed = trimList(sma20, startIdx);

        log.info("Analysis complete for {}: GBM median={}, GARCH σ={}%, Sharpe={}",
            symbol,
            String.format("%.2f", mcResult.getGbmMedianPrice()),
            String.format("%.2f", garchResult.getCurrentAnnualisedVolatility() * 100),
            String.format("%.2f", sharpeResult.getSharpeRatio()));

        return FintechAnalysisResult.builder()
                // Metadata
                .symbol(history.getSymbol())
                .companyName(history.getCompanyName())
                .currentPrice(currentPrice)
                .currency(history.getCurrency())
                .daysAhead(daysAhead)

                // GBM Monte Carlo
                .gbmMedianPrice(mcResult.getGbmMedianPrice())
                .gbmUpperPrice(mcResult.getGbmUpperPrice())
                .gbmLowerPrice(mcResult.getGbmLowerPrice())
                .drift(mcResult.getDrift())
                .volatility(mcResult.getVolatility())
                .medianPath(mcResult.getMedianPath())
                .upperBandPath(mcResult.getUpperPath())
                .lowerBandPath(mcResult.getLowerPath())
                .forecastTimestamps(forecastTimestamps)

                // GARCH
                .garchVolatilitySeries(garchResult.getDailyVolatilitySeries())
                .garchAnnualisedVolatility(garchResult.getCurrentAnnualisedVolatility())
                .garchCurrentVolatility(garchResult.getCurrentDailyVolatility())

                // VaR
                .var95Pct(mcResult.getVar95Pct())
                .var95Abs(var95Abs)
                .var99Pct(mcResult.getVar99Pct())
                .var99Abs(var99Abs)

                // Sharpe
                .sharpeRatio(sharpeResult.getSharpeRatio())
                .sharpeLabel(sharpeResult.getLabel())

                // Bollinger Bands (trimmed to chart window)
                .bollingerMiddle(bbMiddle)
                .bollingerUpper(bbUpper)
                .bollingerLower(bbLower)
                .bollingerSignal(bollingerResult.getSignal())

                // A-Level mode
                .sma20(sma20Trimmed)
                .regressionLine(aLevelResult.getRegressionLine())
                .upperBand1Sigma(aLevelResult.getUpperBand1Sigma())
                .lowerBand1Sigma(aLevelResult.getLowerBand1Sigma())
                .upperBand2Sigma(aLevelResult.getUpperBand2Sigma())
                .lowerBand2Sigma(aLevelResult.getLowerBand2Sigma())
                .rSquared(aLevelResult.getRSquared())
                .regressionPrediction(aLevelResult.getRegressionPrediction())

                // Historical data for chart
                .historicalPrices(chartPrices)
                .historicalTimestamps(chartTimestamps)

                // Sentiment
                .sentimentScore(resolvedSentimentScore)
                .sentimentLabel(sentiment.getOverallLabel())
                .sentimentAdjustedPrice(sentimentAdjustedPrediction)

                .build();
    }

    /**
     * Generate Unix timestamps (seconds) for each future trading day.
     * Assumes ~1 trading day = 86400 seconds (skips weekends approximately).
     * For a production system you'd use a proper trading calendar.
     *
     * @param lastHistoricalTimestamp Unix timestamp of the last historical day
     * @param daysAhead number of future days
     * @return list of future timestamps
     */
    private List<Long> generateForecastTimestamps(long lastHistoricalTimestamp, int daysAhead) {
        List<Long> timestamps = new ArrayList<>();
        // Add 1 day at a time (86400 seconds = 1 day)
        // This is approximate — production systems use proper trading calendars
        for (int i = 1; i <= daysAhead; i++) {
            timestamps.add(lastHistoricalTimestamp + (long) i * 86400);
        }
        return timestamps;
    }

    /**
     * Trim a list to start from the given index.
     * Returns a new list starting at startIdx.
     */
    private List<Double> trimList(List<Double> list, int startIdx) {
        if (startIdx >= list.size()) return new ArrayList<>();
        return new ArrayList<>(list.subList(startIdx, list.size()));
    }
}
